"""Activity: Generate keyframe image prompts from storyboard with AI enrichment."""

import json
from temporalio import activity
from src.services.llm_service import get_fashion_fixture


@activity.defn
async def generate_keyframe_prompts(task_id: str, storyboard: dict) -> dict:
    """Compile keyframe image generation prompts enriched by AI.

    For ai_image / ai_video shots with missing or non-English prompts:
    calls AI to translate scene + action + editInstruction into English
    image generation prompts with fashion-specific negative prompts.
    For shots with existing valid prompts: passes through with enhancements.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("keyframe_prompts"):
        result = get_fashion_fixture("keyframe_prompts")
        activity.logger.info("Keyframe prompts (fake): count=%d", len(result.get("prompts", [])))
        return result

    shots = storyboard.get("shots", [])
    target_shot_nos = set(storyboard.get("targetShotNos", []))

    # Separate shots that need AI enrichment from those with usable prompts
    enrichment_shots = []
    ready_prompts = []

    for shot in shots:
        shot_no = shot.get("shotNo", 0)
        if target_shot_nos and shot_no not in target_shot_nos:
            continue

        material_type = shot.get("materialType", "")
        existing_prompt = shot.get("prompt", "")

        if material_type in ("ai_image", "ai_video") and _needs_enrichment(existing_prompt):
            enrichment_shots.append(_build_shot_input(shot))
        else:
            ready_prompts.append(_build_ready_prompt(shot))

    # AI enrichment for shots with weak/missing prompts
    ai_prompts = []
    if enrichment_shots:
        try:
            from src.services.llm_service import call_llm
            from src.services.validation_pipeline import extract_json

            system_prompt = """你是 KeyframePromptTranslator，将中文分镜信息转换为英文图片生成 prompt。

你会收到多个镜头的结构化信息，每个镜头包含：
- shotNo
- scene：中文场景描述
- action：中文动作描述
- editInstruction：中文剪辑指令
- structureRole：该镜头的叙事功能（hook / product_intro / outfit_reveal / detail_proof / ending 等）
- existingPrompt：可能为空或为中文文本

你的任务是为每个镜头生成一个英文 image generation prompt 和一个英文 negativePrompt，并判断该关键帧的用途。

━━━━━━━━
prompt 规则
━━━━━━━━

prompt 使用英文。包含：
1. 主体：人物或商品。如果涉及商品，必须保持颜色、图案、版型与 scene 描述一致，不做任何修改
2. 场景和环境
3. 一个静态瞬间：从 action 中提取最代表该镜头的静态姿态
4. 景别和构图：从 editInstruction 和 scene 中提取
5. 光线
6. 9:16 vertical format

风格：流畅自然的英文，适合主流图片生成模型。不堆砌 "high quality, 4K, masterpiece"。

如果 existingPrompt 是英文且质量尚可，在其基础上补充完善，不必重写。

━━━━━━━━
negativePrompt 规则
━━━━━━━━

negativePrompt 使用英文。至少包含：
blurry, low quality, watermark, text, logo,
wrong garment color, altered print, misplaced pattern

根据镜头类型补充：
- 人物镜头：deformed face, distorted hands, extra fingers, deformed body
- 商品特写：wrinkled fabric, synthetic-looking fabric, lost detail
- 背面镜头：wrong back design, missing back details

不要所有镜头机械复制完全相同的 negativePrompt。

━━━━━━━━
purpose 判断
━━━━━━━━

根据 structureRole 判断该关键帧的用途：
- hook / setup：first_frame
- product_intro / outfit_reveal：first_frame
- detail_proof：product_detail
- ending / payoff：last_frame
- 不确定时：first_frame

可选值：first_frame / last_frame / reference / product_detail

━━━━━━━━
约束
━━━━━━━━

- 不修改商品颜色、图案、版型
- 不添加 scene 中不存在的元素
- 不编造商品功能
- prompt 和 negativePrompt 使用英文

━━━━━━━━
输出
━━━━━━━━

只返回 JSON：{"prompts": [{"shotNo": 1, "purpose": "first_frame", "prompt": "English prompt...", "negativePrompt": "English negative prompt..."}]}"""

            shots_json = json.dumps(enrichment_shots, ensure_ascii=False, indent=2)
            user_prompt = f"请将以下镜头的分镜信息转换为英文图片生成 prompt。\n\n{shots_json}"

            raw = await call_llm("keyframe_prompts", system_prompt, user_prompt)
            ai_result = raw if isinstance(raw, dict) else {}
            ai_prompts = ai_result.get("prompts", [])
            activity.logger.info("AI keyframe prompts generated: count=%d", len(ai_prompts))
        except Exception as e:
            activity.logger.warning("AI keyframe prompt generation failed, using fallback: %s", e)
            ai_prompts = [_fallback_prompt(s) for s in enrichment_shots]

    # Merge AI results with ready prompts
    ai_map = {p.get("shotNo"): p for p in ai_prompts}
    all_prompts = []

    for shot in shots:
        shot_no = shot.get("shotNo", 0)
        if target_shot_nos and shot_no not in target_shot_nos:
            continue

        if shot_no in ai_map:
            ai_p = ai_map[shot_no]
            all_prompts.append({
                "shotNo": shot_no,
                "purpose": ai_p.get("purpose") or _default_purpose(shot),
                "prompt": ai_p.get("prompt") or _fallback_prompt_text(shot),
                "negativePrompt": ai_p.get("negativePrompt") or _default_negative(),
            })
        else:
            # Already ready from earlier pass
            for rp in ready_prompts:
                if rp["shotNo"] == shot_no:
                    all_prompts.append(rp)
                    break

    result = {"prompts": all_prompts}
    activity.logger.info("Keyframe prompts compiled: total=%d, ai_enriched=%d",
                         len(all_prompts), len(enrichment_shots))
    return result


def _needs_enrichment(prompt: str) -> bool:
    """Check if a prompt needs AI enrichment.

    A prompt needs enrichment if it's empty or contains Chinese characters
    (indicating it's a scene/action fallback, not a proper English prompt).
    """
    if not prompt or not prompt.strip():
        return True
    # If prompt contains Chinese characters, it needs translation
    return any('一' <= c <= '鿿' for c in prompt)


def _build_shot_input(shot: dict) -> dict:
    """Extract fields needed for AI prompt generation."""
    return {
        "shotNo": shot.get("shotNo"),
        "scene": shot.get("scene", ""),
        "action": shot.get("action", ""),
        "editInstruction": shot.get("editInstruction", ""),
        "structureRole": shot.get("structureRole", ""),
        "existingPrompt": shot.get("prompt", ""),
    }


def _build_ready_prompt(shot: dict) -> dict:
    """Build prompt entry for shots with usable existing prompts."""
    return {
        "shotNo": shot.get("shotNo", 0),
        "purpose": _default_purpose(shot),
        "prompt": shot.get("prompt", ""),
        "negativePrompt": shot.get("negativePrompt", "") or _default_negative(),
    }


def _default_purpose(shot: dict) -> str:
    role = shot.get("structureRole", "")
    if role in ("detail_proof",):
        return "product_detail"
    if role in ("ending", "payoff", "cta"):
        return "last_frame"
    return "first_frame"


def _default_negative() -> str:
    return "blurry, low quality, watermark, text, logo, wrong garment color, altered print, deformed body, extra fingers"


def _fallback_prompt(shot_input: dict) -> dict:
    return {
        "shotNo": shot_input["shotNo"],
        "purpose": _default_purpose(shot_input),
        "prompt": _fallback_prompt_text(shot_input),
        "negativePrompt": _default_negative(),
    }


def _fallback_prompt_text(shot: dict) -> str:
    scene = shot.get("scene", "")
    action = shot.get("action", "")
    text = f"{scene}. {action}".strip()
    if text:
        return f"{text}, 9:16 vertical"
    return f"Shot {shot.get('shotNo', 0)} keyframe, 9:16 vertical"
