"""Activity: Generate fashion creative plans based on asset analysis."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import CreativePlanResult


@activity.defn
async def generate_fashion_plans(task_id: str, analysis: dict, product_context: dict) -> dict:
    """Generate 3-5 fashion creative plans based on asset analysis.

    In fake mode, returns fixture data matching CreativePlanResult schema.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("fashion_plans"):
        result = get_fashion_fixture("fashion_plans")
    else:
        from src.services.llm_service import call_llm
        from src.services.validation_pipeline import validate_and_repair

        system_prompt = (
            "You are a TikTok fashion video creative strategist. Generate 3-5 video plans. "
            "Do not start from a fixed template. First make each plan meaningfully different in "
            "audience angle, hook, asset usage, pacing, and proof style. "
            "The plan type is an internal strategy/rendering hint, not the creative structure itself. "
            "Each plan: type (pain_point_solution/before_after/review/product_showcase/ugc_style/tutorial), "
            "title (string), hook (1-120 chars), structure (string), reason (string), "
            "estimatedDuration (15-30), score (0-100), "
            "taskMode (PRODUCT_CREATIVE/REFERENCE_STORYBOARD/USER_SCRIPT/CUSTOM_STORYBOARD), "
            "requiredAssets (array of asset roles), estimatedCostTier (cheap/moderate/expensive). "
            "Strict JSON only, no markdown."
        )
        user_prompt = f"Product: {product_context}\n\nAsset Analysis: {analysis}"
        result = validate_and_repair(
            await call_llm("fashion_plans", system_prompt, user_prompt),
            CreativePlanResult,
        ).model_dump()

    activity.logger.info("Fashion plans generated: count=%d", len(result.get("plans", [])))
    return result
