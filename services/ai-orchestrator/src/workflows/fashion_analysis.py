"""FashionAnalysisWorkflow: analyze assets or reference video → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import analyze_fashion_assets, analyze_reference_video, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class FashionAnalysisWorkflow:
    """Analyze fashion assets or reference video, then callback to Java.

    Supports two analysis types:
    - "asset": analyze product images → callback stage "asset_analysis"
    - "reference": analyze reference video → callback stage "reference_analysis"
    """

    @workflow.run
    async def run(
        self,
        task_id: str,
        product_context: dict,
        analysis_type: str = "asset",
        reference_url: str = "",
    ) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        callback_retry = RetryPolicy(maximum_attempts=3)

        if analysis_type == "reference":
            # ── Reference video analysis ──
            try:
                ref_analysis = await workflow.execute_activity(
                    "analyze_reference_video",
                    args=[task_id, reference_url],
                    start_to_close_timeout=timedelta(minutes=2),
                    retry_policy=retry,
                )
            except Exception as e:
                await workflow.execute_activity(
                    "callback_java",
                    build_failed_callback_payload(task_id, "reference_analysis", "REFERENCE_ANALYSIS_FAILED", str(e), True),
                    start_to_close_timeout=timedelta(seconds=30),
                    retry_policy=callback_retry,
                )
                return {"status": "failed", "stage": "reference_analysis", "error": str(e)}

            await workflow.execute_activity(
                "callback_java",
                build_callback_payload(
                    task_id, "reference_analysis", "success", "plan_generating",
                    referenceAnalysis=ref_analysis,
                ),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "completed", "stage": "reference_analysis"}

        # ── Asset analysis ──
        try:
            asset_analysis = await workflow.execute_activity(
                "analyze_fashion_assets",
                args=[task_id, product_context],
                start_to_close_timeout=timedelta(minutes=5),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "asset_analysis", "ASSET_ANALYSIS_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "asset_analysis", "error": str(e)}

        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "asset_analysis", "success", "waiting_asset_confirmation",
                fashionAssetAnalysis=asset_analysis,
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=callback_retry,
        )
        return {"status": "completed", "stage": "asset_analysis"}
