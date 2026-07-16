"""Activity: Generate keyframe images using FakeImageProvider (development phase).

M4 uses FakeImageProvider exclusively — real image generation for keyframes
will be enabled in a later milestone via ENABLE_IMAGE_GENERATION.
"""

from temporalio import activity
from src.providers.fake_image import FakeImageProvider
from src.schemas.ai_outputs import KeyframeGenerationResult


@activity.defn
async def fake_generate_keyframes(task_id: str, prompts: dict, storyboard: dict) -> dict:
    """Generate keyframe images for each shot.

    Always uses FakeImageProvider in M4. Individual per-shot failures
    are captured as failed keyframe entries rather than aborting the entire batch.

    Returns a KeyframeGenerationResult-compatible dict with 'keyframes' array.
    """
    provider = FakeImageProvider()
    prompts_list = prompts.get("prompts", [])
    keyframes = []

    for p in prompts_list:
        shot_no = p.get("shotNo", 0)
        purpose = p.get("purpose", "first_frame")
        prompt_text = p.get("prompt", "")
        negative = p.get("negativePrompt", "")

        try:
            result = await provider.generate(
                prompt=prompt_text or f"Fashion keyframe shot {shot_no}",
                negative_prompt=negative or "blurry, low quality, watermark, text",
                shot_no=shot_no,
                purpose=purpose,
            )
            keyframes.append({
                "shotNo": shot_no,
                "status": "completed",
                "url": result.get("url", ""),
                "prompt": prompt_text,
                "negativePrompt": negative,
                "provider": result.get("provider", provider.provider_name),
                "modelName": result.get("model", "unknown"),
                "qualityScore": result.get("qualityScore"),
                "source": "ai_generated",
                "imagePurpose": purpose,
            })
            activity.logger.info("Keyframe generated: shot=%d, provider=%s, url=%s",
                                 shot_no, result.get("provider"), result.get("url"))
        except Exception as e:
            activity.logger.error("Keyframe generation failed: shot=%d, error=%s", shot_no, e)
            keyframes.append({
                "shotNo": shot_no,
                "status": "failed",
                "prompt": prompt_text,
                "negativePrompt": negative,
                "provider": provider.provider_name,
                "modelName": getattr(provider, "_model", "unknown"),
                "errorMessage": str(e),
                "source": "ai_generated",
                "imagePurpose": purpose,
            })

    result = {"keyframes": keyframes}
    KeyframeGenerationResult.model_validate(result)
    activity.logger.info("Keyframe batch complete: total=%d, completed=%d, failed=%d",
                         len(keyframes),
                         sum(1 for k in keyframes if k["status"] == "completed"),
                         sum(1 for k in keyframes if k["status"] == "failed"))
    return result
