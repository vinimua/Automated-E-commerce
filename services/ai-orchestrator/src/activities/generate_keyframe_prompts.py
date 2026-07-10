"""Activity: Generate keyframe image prompts for each storyboard shot."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture


@activity.defn
async def generate_keyframe_prompts(task_id: str, storyboard: dict) -> dict:
    """Extract and compile keyframe generation prompts from the storyboard.

    This is a pass-through activity: it reads the storyboard's shots and
    compiles image generation prompts for each shot that needs an AI keyframe.
    When fake mode is active, returns fixture prompts.

    Returns a dict with a 'prompts' key containing a list of per-shot prompt dicts.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("keyframe_prompts"):
        result = get_fashion_fixture("keyframe_prompts")
        activity.logger.info("Keyframe prompts (fake): count=%d", len(result.get("prompts", [])))
        return result

    shots = storyboard.get("shots", [])
    target_shot_nos = set(storyboard.get("targetShotNos", []))
    prompts = []
    for shot in shots:
        shot_no = shot.get("shotNo", 0)
        if target_shot_nos and shot_no not in target_shot_nos:
            continue
        material_type = shot.get("materialType", "")
        prompt_text = shot.get("prompt", "")
        negative = shot.get("negativePrompt", "")

        if not prompt_text and material_type in ("ai_image", "ai_video"):
            # Generate a fallback prompt from scene + action
            scene = shot.get("scene", "")
            action = shot.get("action", "")
            prompt_text = f"{scene}. {action}".strip()

        prompts.append({
            "shotNo": shot_no,
            "purpose": "first_frame",
            "prompt": prompt_text or f"Shot {shot_no} keyframe",
            "negativePrompt": negative or "blurry, low quality, watermark, text",
        })

    result = {"prompts": prompts}
    activity.logger.info("Keyframe prompts compiled: count=%d", len(prompts))
    return result
