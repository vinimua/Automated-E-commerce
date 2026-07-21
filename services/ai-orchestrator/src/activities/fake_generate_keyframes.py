"""Activity: Generate keyframe images — real or fake provider based on config.

When ENABLE_IMAGE_GENERATION=true, uses Volcengine / OpenAI real image generation.
Otherwise falls back to FakeImageProvider (zero-cost SVG placeholders).
"""

from temporalio import activity
from src.providers.factory import get_image_provider
from src.schemas.ai_outputs import KeyframeGenerationResult


@activity.defn
async def fake_generate_keyframes(task_id: str, prompts: dict, storyboard: dict) -> dict:
    """Generate keyframe images for each shot.

    Routes to the configured provider (real or fake). Individual per-shot
    failures are captured as failed keyframe entries rather than aborting
    the entire batch.

    Returns a KeyframeGenerationResult-compatible dict with 'keyframes' array.
    """
    provider = get_image_provider()
    prompts_list = prompts.get("prompts", [])
    # Collect product image URLs from storyboard for img2img reference
    source_assets = _collect_source_assets(storyboard)
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
                source_assets=source_assets,
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
            provider_name = getattr(provider, "provider_name", getattr(provider, "_model", "unknown"))
            model_name = getattr(provider, "_model", "unknown")
            keyframes.append({
                "shotNo": shot_no,
                "status": "failed",
                "prompt": prompt_text,
                "negativePrompt": negative,
                "provider": provider_name,
                "modelName": model_name,
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


def _collect_source_assets(storyboard: dict) -> list[dict]:
    """Collect product image URLs from storyboard context for img2img reference."""
    assets = []
    # Shots may have keyframeUrl references from previous keyframe generation
    for shot in (storyboard.get("shots") or []):
        kf_url = shot.get("keyframeUrl")
        if kf_url:
            assets.append({"url": kf_url})
    # Storyboard may have a top-level assets list
    for a in (storyboard.get("assets") or []):
        if isinstance(a, dict) and a.get("url"):
            assets.append(a)
    return assets
