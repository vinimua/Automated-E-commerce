"""Activity: Fake video clip generation — returns placeholder video URLs.

Controlled by ENABLE_VIDEO_GENERATION feature flag.
When disabled (default), returns fixture placeholder URLs.
"""

from temporalio import activity
from src.config import settings
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import VideoClipGenerationResult


@activity.defn
async def fake_generate_video_clips(task_id: str, prompts: dict, storyboard: dict) -> dict:
    """Generate video clips for each shot. Uses fake provider when video generation is disabled.

    Returns a VideoClipGenerationResult-compatible dict with 'clips' array.
    """
    if not settings.enable_video_generation:
        result = get_fashion_fixture("fake_video_clips")
        activity.logger.info("Fake video clips generated: count=%d (ENABLE_VIDEO_GENERATION=false)",
                             len(result.get("clips", [])))
        # Validate against schema
        VideoClipGenerationResult.model_validate(result)
        return result

    # Real video generation path (M6)
    activity.logger.warning("Real video generation not yet implemented — falling back to fake")
    result = get_fashion_fixture("fake_video_clips")
    return result
