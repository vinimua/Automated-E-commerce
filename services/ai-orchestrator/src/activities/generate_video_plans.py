"""Activity: Generate 3-5 video plans."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.services.validation_pipeline import validate_and_repair
from src.schemas.ai_outputs import VideoPlanResult


@activity.defn
async def generate_video_plans(analysis: dict) -> dict:
    system_prompt = (
        "You are a TikTok video strategist. Generate 3-5 video plans. "
        "Each plan: type (pain_point_solution/before_after/review), title, "
        "hook (max 120 chars), structure, reason, estimatedDuration (15-30), score (0-100). "
        "Strict JSON only, no markdown."
    )
    user_prompt = f"Create video plans based on this analysis:\n{analysis}"

    try:
        result = validate_and_repair(
            await call_llm("video_plans", system_prompt, user_prompt),
            VideoPlanResult,
        )
    except ValueError as e:
        activity.logger.error("VideoPlanResult validation failed: %s", e)
        raise

    return result.model_dump()
