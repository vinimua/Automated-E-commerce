"""Activity: Generate AI image assets."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.services.validation_pipeline import validate_and_repair
from src.schemas.ai_outputs import MaterialResult


@activity.defn
async def generate_image_assets(storyboard: dict) -> dict:
    """Generate images for shots that need them. In fake mode, returns fixture data."""
    try:
        result = validate_and_repair(
            await call_llm("materials", "", str(storyboard)),
            MaterialResult,
        )
    except ValueError as e:
        activity.logger.error("MaterialResult validation failed: %s", e)
        raise

    return result.model_dump()
