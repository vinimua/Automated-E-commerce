"""Activity: Generate video clip prompts from storyboard + keyframe context with AI enrichment."""

import json
from temporalio import activity


@activity.defn
async def generate_video_clip_prompts(task_id: str, storyboard: dict, keyframes: dict) -> dict:
    """Compile video clip generation prompts enriched by AI.

    For ai_video shots: calls AI to translate storyboard + keyframe info
    into Chinese video generation prompts with English negative prompts.
    For non-ai_video shots: uses existing prompts or rule-based fallback.
    """
    from src.services.llm_service import _is_fake_mode, get_fashion_fixture

    shots = storyboard.get("shots", [])
    kf_list = keyframes.get("keyframes", [])
    target_shot_nos = set(storyboard.get("targetShotNos") or keyframes.get("targetShotNos") or [])
    kf_map = {k.get("shotNo"): k for k in kf_list}

    # Separate ai_video shots from others
    ai_video_shots = []
    static_prompts = []

    for shot in shots:
        shot_no = shot.get("shotNo", 0)
        if target_shot_nos and shot_no not in target_shot_nos:
            continue

        material_type = shot.get("materialType", "")
        kf = kf_map.get(shot_no, {})

        if material_type == "ai_video":
            ai_video_shots.append(_build_shot_input(shot, kf))
        else:
            static_prompts.append(_build_static_prompt(shot, kf))

    # AI enrichment for ai_video shots
    ai_prompts = []
    if ai_video_shots:
        if _is_fake_mode("video_clip_prompts"):
            fixture = get_fashion_fixture("video_clip_prompts")
            fake_prompts = fixture.get("prompts", [])
            fake_map = {p.get("shotNo"): p for p in fake_prompts}
            for s in ai_video_shots:
                fp = fake_map.get(s["shotNo"], {})
                ai_prompts.append({
                    "shotNo": s["shotNo"],
                    "prompt": fp.get("prompt") or f"Shot {s['shotNo']} video clip",
                    "negativePrompt": fp.get("negativePrompt") or "blurry, jitter, watermark, wrong garment color",
                })
        else:
            from src.services.llm_service import call_llm
            from src.services.validation_pipeline import extract_json

            system_prompt = """你是 VideoClipPromptTranslator，将分镜信息转换为视频生成 prompt。

你会收到多个镜头的结构化信息，每个镜头包含：
- shotNo、duration
- scene：中文场景描述
- action：中文动作描述
- editInstruction：中文剪辑/运镜指令
- keyframePrompt：该镜头关键帧的生成 prompt（描述静态画面，作为视频起始/结束画面的锚点）
- keyframePurpose：first_frame / last_frame / reference（静态画面在视频中的角色）

你的任务是为每个镜头生成一个中文 video generation prompt 和一个英文 negativePrompt。

━━━━━━━━
prompt 规则
━━━━━━━━

prompt 使用中文。包含：
1. 画面主体：从 keyframePrompt 中提取商品颜色、图案、版型，保持一致，不做任何修改
2. 场景和环境
3. 动作描述：从 action 和 editInstruction 中提取，描述从关键帧静态画面开始后发生了什么运动
4. 镜头运动：固定/推近/拉远/跟拍/横移——editInstruction 中有明确运镜时必须体现，没有则根据动作合理推断
5. 光线和氛围
6. 9:16 竖屏
7. 时长感：如"一段3秒的视频片段"

如果 keyframePurpose 是 first_frame，prompt 应描述从该静态画面开始的运动过程。
如果 keyframePurpose 是 last_frame，prompt 应描述到达该静态画面的运动过程。

不堆砌"高质量""电影级""4K""杰作"等空洞修饰词。

━━━━━━━━
negativePrompt 规则
━━━━━━━━

negativePrompt 使用英文。至少包含：
wrong garment color, altered print, misplaced pattern, warped fabric,
deformed body, extra limbs, distorted hands, blurry, jitter,
watermark, text, logo, inconsistent outfit across frames

根据镜头类型补充：
- 特写镜头：blurred fabric texture, lost detail
- 行走/转身镜头：unnatural walk cycle, stiff turning, garment clipping
- 背面镜头：wrong back design, missing back details

不要所有镜头机械复制完全相同的 negativePrompt。

━━━━━━━━
约束
━━━━━━━━

- 不修改商品颜色、图案、版型
- 不添加 scene 中不存在的元素（口袋、拉链、装饰、配饰）
- 不编造商品功能
- prompt 使用中文，negativePrompt 使用英文

━━━━━━━━
输出
━━━━━━━━

只返回 JSON：{"prompts": [{"shotNo": 1, "prompt": "中文 prompt...", "negativePrompt": "English negative prompt..."}]}"""

            shots_json = json.dumps(ai_video_shots, ensure_ascii=False, indent=2)
            user_prompt = f"请将以下镜头的分镜信息转换为视频生成 prompt。\n\n{shots_json}"

            try:
                raw = await call_llm("video_clip_prompts", system_prompt, user_prompt)
                ai_result = raw if isinstance(raw, dict) else {}
                ai_prompts = ai_result.get("prompts", [])
                activity.logger.info("AI video clip prompts generated: count=%d", len(ai_prompts))
            except Exception as e:
                activity.logger.warning("AI video clip prompt generation failed, using fallback: %s", e)
                ai_prompts = [_fallback_prompt(s) for s in ai_video_shots]

    # Merge: AI prompts take priority, static prompts use existing rules
    ai_map = {p.get("shotNo"): p for p in ai_prompts}
    all_prompts = []

    for shot in shots:
        shot_no = shot.get("shotNo", 0)
        if target_shot_nos and shot_no not in target_shot_nos:
            continue

        kf = kf_map.get(shot_no, {})
        if shot_no in ai_map:
            ai_p = ai_map[shot_no]
            all_prompts.append({
                "shotNo": shot_no,
                "prompt": ai_p.get("prompt") or _fallback_prompt_text(shot),
                "negativePrompt": ai_p.get("negativePrompt") or "blurry, jitter, watermark, wrong garment color, warped fabric",
                "duration": shot.get("duration", 3),
                "keyframeUrl": kf.get("url", ""),
            })
        else:
            all_prompts.append(_build_static_prompt(shot, kf))

    result = {"prompts": all_prompts}
    activity.logger.info("Video clip prompts compiled: total=%d, ai_enriched=%d",
                         len(all_prompts), len(ai_video_shots))
    return result


def _build_shot_input(shot: dict, kf: dict) -> dict:
    """Extract the fields needed for AI prompt generation."""
    return {
        "shotNo": shot.get("shotNo"),
        "duration": shot.get("duration", 3),
        "scene": shot.get("scene", ""),
        "action": shot.get("action", ""),
        "editInstruction": shot.get("editInstruction", ""),
        "keyframePrompt": kf.get("prompt", ""),
        "keyframePurpose": kf.get("imagePurpose", "first_frame"),
    }


def _build_static_prompt(shot: dict, kf: dict) -> dict:
    """Build prompt for non-ai_video shots using existing data."""
    shot_no = shot.get("shotNo", 0)
    prompt_text = shot.get("prompt", "")
    negative = shot.get("negativePrompt", "")

    if not prompt_text:
        scene = shot.get("scene", "")
        action = shot.get("action", "")
        prompt_text = f"{scene}. {action}".strip() or f"Shot {shot_no} video clip"

    return {
        "shotNo": shot_no,
        "prompt": prompt_text,
        "negativePrompt": negative or "blurry, low quality, watermark, text, jitter",
        "duration": shot.get("duration", 3),
        "keyframeUrl": kf.get("url", ""),
    }


def _fallback_prompt(shot_input: dict) -> dict:
    """Fallback when AI call fails."""
    return {
        "shotNo": shot_input["shotNo"],
        "prompt": _fallback_prompt_text(shot_input),
        "negativePrompt": "blurry, jitter, watermark, wrong garment color, altered print, warped fabric, deformed body",
    }


def _fallback_prompt_text(shot: dict) -> str:
    scene = shot.get("scene", "")
    action = shot.get("action", "")
    duration = shot.get("duration", 3)
    text = f"{scene}. {action}".strip()
    if text:
        return f"{text}，{duration}秒竖屏视频"
    return f"Shot {shot.get('shotNo', 0)} video clip, {duration}s"
