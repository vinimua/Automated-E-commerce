"""Activity: Generate video clip prompts for each storyboard shot based on keyframes."""

from temporalio import activity
@activity.defn
async def generate_video_clip_prompts(task_id: str, storyboard: dict, keyframes: dict) -> dict:
    """Compile video clip generation prompts from storyboard + keyframe context.

    This is a pass-through activity: it enriches each shot's prompt with
    keyframe information for video generation.
    Returns a dict with a 'prompts' key containing a list of per-shot prompt dicts.
    """
    shots = storyboard.get("shots", [])
    kf_list = keyframes.get("keyframes", [])
    target_shot_nos = set(storyboard.get("targetShotNos") or keyframes.get("targetShotNos") or [])
    kf_map = {k.get("shotNo"): k for k in kf_list}

    prompts = []
    for shot in shots:
        shot_no = shot.get("shotNo", 0)
        if target_shot_nos and shot_no not in target_shot_nos:
            continue
        material_type = shot.get("materialType", "")
        prompt_text = shot.get("prompt", "")
        negative = shot.get("negativePrompt", "")
        duration = shot.get("duration", 3)

        # Enrich prompt with keyframe context
        kf = kf_map.get(shot_no, {})
        kf_url = kf.get("url", "")

        if not prompt_text and material_type in ("ai_image", "ai_video"):
            scene = shot.get("scene", "")
            action = shot.get("action", "")
            prompt_text = f"{scene}. {action}".strip()

        prompts.append({
            "shotNo": shot_no,
            "prompt": prompt_text or f"Shot {shot_no} video clip",
            "negativePrompt": negative or "blurry, low quality, watermark, text, jitter",
            "duration": duration,
            "keyframeUrl": kf_url,
        })

    result = {"prompts": prompts}
    activity.logger.info("Video clip prompts compiled: count=%d", len(prompts))
    return result
