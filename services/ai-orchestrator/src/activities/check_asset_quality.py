"""Activity: Quality check on generated assets."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.services.validation_pipeline import validate_and_repair
from src.schemas.ai_outputs import QualityCheckResult


@activity.defn
async def check_asset_quality(storyboard: dict, materials: dict) -> dict:
    """Run quality check on all generated assets. Fake mode returns fixture data."""
    combined = {"storyboard": storyboard, "materials": materials}
    try:
        result = validate_and_repair(
            await call_llm("quality_check", "", str(combined)),
            QualityCheckResult,
        )
    except ValueError as e:
        activity.logger.error("QualityCheckResult validation failed: %s", e)
        raise

    return result.model_dump()
