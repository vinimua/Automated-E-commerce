"""FashionRepairWorkflow: classify_feedback → plan_repair → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import classify_feedback, plan_repair, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


def _next_status_for_repair(target_type: str) -> str:
    if target_type == "keyframe":
        return "image_generating"
    if target_type == "video_clip":
        return "video_clip_generating"
    if target_type in ("render_manifest", "final_video"):
        return "rendering"
    return "keyframe_configuring"


@workflow.defn
class FashionRepairWorkflow:
    """Classify user feedback and plan repair actions, then callback to Java.

    Flow:
    1. classify_feedback → structured RepairResult
    2. plan_repair → concrete repair plan with affected shots
    3. callback_java: stage="repair", nextTaskStatus=target-specific regeneration state
    """

    @workflow.run
    async def run(
        self,
        task_id: str,
        feedback_text: str,
        category: str,
        target_type: str,
        current_state: dict,
    ) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        callback_retry = RetryPolicy(maximum_attempts=3)

        # Step 1: Classify the feedback
        try:
            classification = await workflow.execute_activity(
                "classify_feedback",
                args=[task_id, feedback_text, category, target_type],
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "repair", "FEEDBACK_CLASSIFICATION_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "repair", "error": str(e)}

        # Step 2: Plan the repair
        try:
            repair_plan = await workflow.execute_activity(
                "plan_repair",
                args=[task_id, classification, current_state],
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "repair", "REPAIR_PLAN_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "repair", "error": str(e)}

        # Step 3: Callback to Java
        repair_event_id = current_state.get("repairEventId")
        if repair_event_id:
            repair_plan = {**repair_plan, "repairEventId": repair_event_id}
        next_status = _next_status_for_repair(repair_plan.get("targetType", target_type))

        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "repair", "success", next_status,
                repairResult=repair_plan,
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=callback_retry,
        )

        return {"status": "completed", "stage": "repair"}
