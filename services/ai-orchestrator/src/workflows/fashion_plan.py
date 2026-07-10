"""FashionPlanWorkflow: generate_fashion_plans → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import generate_fashion_plans, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class FashionPlanWorkflow:
    """Generate fashion creative plans, then callback to Java.

    Flow:
    1. generate_fashion_plans → 3-5 creative plans
    2. callback_java: stage="creative_plan", nextTaskStatus="waiting_plan_selection"
    """

    @workflow.run
    async def run(
        self,
        task_id: str,
        product_context: dict,
        asset_analysis: dict,
    ) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        callback_retry = RetryPolicy(maximum_attempts=3)

        # Step 1: Generate fashion creative plans
        try:
            plans_result = await workflow.execute_activity(
                "generate_fashion_plans",
                args=[task_id, asset_analysis, product_context],
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "creative_plan", "PLAN_GENERATION_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "creative_plan", "error": str(e)}

        # Step 2: Callback to Java
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "creative_plan", "success", "waiting_plan_selection",
                plans=plans_result.get("plans", []),
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=callback_retry,
        )

        return {"status": "completed", "stage": "creative_plan"}
