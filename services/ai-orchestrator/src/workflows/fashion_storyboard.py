"""FashionStoryboardWorkflow: generate_fashion_storyboard → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import generate_fashion_storyboard, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class FashionStoryboardWorkflow:
    """Generate fashion storyboard from selected plan, then callback to Java.

    Flow:
    1. generate_fashion_storyboard → shot-by-shot breakdown
    2. callback_java: stage="storyboard", nextTaskStatus="waiting_storyboard_confirmation"
    """

    @workflow.run
    async def run(
        self,
        task_id: str,
        product_context: dict,
        selected_plan: dict,
        duration: int,
        video_type: str,
    ) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        callback_retry = RetryPolicy(maximum_attempts=3)

        # Step 1: Generate fashion storyboard
        try:
            storyboard = await workflow.execute_activity(
                "generate_fashion_storyboard",
                args=[task_id, selected_plan, product_context, duration, video_type],
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "storyboard", "STORYBOARD_GENERATION_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "storyboard", "error": str(e)}

        # Step 2: Callback to Java
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "storyboard", "success", "waiting_storyboard_confirmation",
                storyboard=storyboard,
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=callback_retry,
        )

        return {"status": "completed", "stage": "storyboard"}
