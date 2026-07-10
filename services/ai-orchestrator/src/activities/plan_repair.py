"""Activity: Plan repair actions based on classified feedback.

Controlled by ENABLE_LANGGRAPH_REPAIR feature flag.
When disabled (default), uses fixture repair plan.
"""

from temporalio import activity
from src.config import settings
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import RepairResult


@activity.defn
async def plan_repair(task_id: str, classification: dict, current_state: dict) -> dict:
    """Create a concrete repair plan based on classified feedback.

    Takes the classified feedback and current task state, and produces
    an actionable repair plan with specific regeneration targets.

    Returns a RepairResult-compatible dict.
    """
    if not settings.enable_langgraph_repair:
        result = get_fashion_fixture("repair_plan")
        # Merge classification info
        result["feedbackCategory"] = classification.get("feedbackCategory", result["feedbackCategory"])
        result["targetType"] = classification.get("targetType", result["targetType"])
        result["strategy"] = classification.get("strategy", result["strategy"])
        result["affectedShots"] = classification.get("affectedShots", result["affectedShots"])
        activity.logger.info("Repair planned (fake): strategy=%s, affectedShots=%s",
                             result.get("strategy"), result.get("affectedShots"))
        RepairResult.model_validate(result)
        return result

    # Real LangGraph repair path (M8)
    activity.logger.warning("LangGraph repair not yet implemented — falling back to fake repair plan")
    result = get_fashion_fixture("repair_plan")
    return result
