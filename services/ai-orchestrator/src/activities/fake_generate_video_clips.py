"""Activity: Generate video clips — real or fake provider based on config.

When ENABLE_VIDEO_GENERATION=true, uses Volcengine / Seedance real video generation.
Otherwise falls back to deterministic fake output (placeholder flower.mp4).
"""

from temporalio import activity

from src.config import settings
from src.providers.factory import get_video_provider
from src.schemas.ai_outputs import VideoClipGenerationResult


FAKE_VIDEO_URL = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"


@activity.defn
async def fake_generate_video_clips(task_id: str, prompts: dict, storyboard: dict, keyframes: dict = None) -> dict:
    """Generate video clips for each shot.

    Routes to the configured provider (real or fake). Individual per-shot
    failures are captured as failed clip entries rather than aborting
    the entire batch.

    When keyframes are provided, the confirmed keyframe image is passed
    as source_images for image-to-video generation.
    """
    provider = get_video_provider()
    prompt_items = prompts.get("prompts", [])

    # Fallback: if prompt compilation was empty, generate from storyboard shots directly
    if not prompt_items:
        activity.logger.warning("No prompts in compiled list, falling back to storyboard shots")
        shots = storyboard.get("shots", [])
        for shot in shots:
            shot_no = shot.get("shotNo", 0)
            if shot_no <= 0:
                continue
            prompt_items.append({
                "shotNo": shot_no,
                "prompt": shot.get("prompt") or f"Shot {shot_no} video clip",
                "negativePrompt": shot.get("negativePrompt", ""),
                "duration": shot.get("duration", 3),
            })

    # Build keyframe lookup by shotNo for image-to-video reference
    keyframe_by_shot: dict[int, dict] = {}
    if keyframes:
        for kf in keyframes.get("keyframes", []):
            sn = kf.get("shotNo", 0)
            url = kf.get("url")
            if sn > 0 and url:
                keyframe_by_shot[sn] = kf

    clips = []
    for item in prompt_items:
        shot_no = int(item.get("shotNo") or 0)
        if shot_no <= 0:
            continue

        prompt_text = item.get("prompt") or f"Shot {shot_no} video clip"
        negative = item.get("negativePrompt") or "blurry, low quality, watermark, text, jitter"
        duration = int(item.get("duration") or 3)

        # Use confirmed keyframe as first-frame reference for image-to-video
        source_images = []
        kf = keyframe_by_shot.get(shot_no)
        if kf and kf.get("url"):
            source_images = [{"url": kf["url"]}]

        try:
            result = await provider.generate(
                prompt=prompt_text,
                negative_prompt=negative,
                shot_no=shot_no,
                duration=duration,
                source_images=source_images,
            )
            clips.append({
                "shotNo": shot_no,
                "status": "completed",
                "url": result.get("url", f"{FAKE_VIDEO_URL}#task={task_id}&shot={shot_no}"),
                "prompt": prompt_text,
                "negativePrompt": negative,
                "provider": result.get("provider", provider.provider_name),
                "modelName": result.get("model", "unknown"),
                "duration": duration,
                "source": "ai_generated",
            })
            activity.logger.info("Video clip generated: shot=%d, provider=%s", shot_no, result.get("provider"))
        except Exception as e:
            activity.logger.error("Video clip generation failed: shot=%d, error=%s", shot_no, e)
            provider_name = getattr(provider, "provider_name", "unknown")
            model_name = getattr(provider, "_model", "unknown")
            clips.append({
                "shotNo": shot_no,
                "status": "failed",
                "prompt": prompt_text,
                "negativePrompt": negative,
                "provider": provider_name,
                "modelName": model_name,
                "errorMessage": str(e),
                "source": "ai_generated",
                "duration": duration,
            })

    result = {"clips": clips}
    VideoClipGenerationResult.model_validate(result)
    activity.logger.info("Video clip batch complete: total=%d, completed=%d, failed=%d",
                         len(clips),
                         sum(1 for c in clips if c["status"] == "completed"),
                         sum(1 for c in clips if c["status"] == "failed"))
    return result
