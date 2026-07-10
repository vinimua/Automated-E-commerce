"""Activity: Classify user feedback into structured repair strategy.

Controlled by ENABLE_LANGGRAPH_REPAIR feature flag.
When disabled (default), uses fixture classification.
"""

from temporalio import activity
from src.config import settings
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import RepairResult


@activity.defn
async def classify_feedback(
    task_id: str,
    feedback_text: str,
    category: str,
    target_type: str,
) -> dict:
    """Classify user feedback into a RepairResult with strategy and affected shots.

    Returns a RepairResult-compatible dict.
    """
    if not settings.enable_langgraph_repair:
        result = get_fashion_fixture("feedback_classification")
        # Override with actual user input
        result["repairNotes"] = f"User feedback: {feedback_text}"
        activity.logger.info("Feedback classified (fake): category=%s, target=%s, strategy=%s",
                             result.get("feedbackCategory"), result.get("targetType"),
                             result.get("strategy"))
        RepairResult.model_validate(result)
        return result

    # Real LangGraph repair path (M8)
    activity.logger.warning("LangGraph repair not yet implemented — falling back to fake classification")
    result = get_fashion_fixture("feedback_classification")
    result["repairNotes"] = f"User feedback: {feedback_text}"
    return result
