"""ProductAnalysisWorkflow: analyze_product → generate_video_plans → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import analyze_product, generate_video_plans, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class ProductAnalysisWorkflow:
    @workflow.run
    async def run(self, task_id: str, product_context: dict) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )

        # Step 1: Analyze product
        try:
            analysis = await workflow.execute_activity(
                "analyze_product", product_context,
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "product_analysis", "AI_ANALYSIS_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=RetryPolicy(maximum_attempts=3),
            )
            return {"status": "failed", "stage": "product_analysis", "error": str(e)}

        # Callback: product analysis complete → Java saves analysis to product table
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "product_analysis", "success", "analysis_completed",
                productAnalysis=analysis,
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=RetryPolicy(maximum_attempts=3),
        )

        # Step 2: Generate video plans
        try:
            plans = await workflow.execute_activity(
                "generate_video_plans", analysis,
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "video_plan", "AI_PLAN_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
            )
            return {"status": "failed", "stage": "video_plan", "error": str(e)}

        # Step 3: Callback Java with success
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "video_plan", "success", "plan_generated",
                plans=plans.get("plans", []),
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=RetryPolicy(maximum_attempts=3),
        )

        return {"status": "completed", "stage": "video_plan"}
