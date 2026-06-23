"""Activity: Generate AI video clip assets."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.services.validation_pipeline import validate_and_repair
from src.schemas.ai_outputs import MaterialResult


@activity.defn
async def generate_video_clips(storyboard: dict, image_assets: dict) -> dict:
    """Generate video clips. Reuses image assets as fallback. Fake mode returns fixture data."""
    combined = {"storyboard": storyboard, "image_assets": image_assets}
    try:
        result = validate_and_repair(
            await call_llm("materials", "", str(combined)),
            MaterialResult,
        )
    except ValueError as e:
        activity.logger.error("MaterialResult validation failed: %s", e)
        raise

    return result.model_dump()
