"""FashionKeyframeWorkflow: generate prompts → fake_generate_keyframes → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import generate_keyframe_prompts, fake_generate_keyframes, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class FashionKeyframeWorkflow:
    """Generate keyframe images for each storyboard shot, then callback to Java.

    Flow:
    1. generate_keyframe_prompts → compile prompts per shot
    2. fake_generate_keyframes → generate placeholder images (or real in M5)
    3. callback_java: stage="keyframe", nextTaskStatus="waiting_image_confirmation"
    """

    @workflow.run
    async def run(self, task_id: str, storyboard: dict) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        callback_retry = RetryPolicy(maximum_attempts=3)

        # Step 1: Generate keyframe prompts
        try:
            prompts = await workflow.execute_activity(
                "generate_keyframe_prompts",
                args=[task_id, storyboard],
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "keyframe", "PROMPT_GENERATION_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "keyframe", "error": str(e)}

        # Step 2: Generate keyframe images (fake or real)
        try:
            keyframes_result = await workflow.execute_activity(
                "fake_generate_keyframes",
                args=[task_id, prompts, storyboard],
                start_to_close_timeout=timedelta(minutes=5),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "keyframe", "KEYFRAME_GENERATION_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "keyframe", "error": str(e)}

        # Step 3: Callback to Java
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "keyframe", "success", "waiting_image_confirmation",
                keyframes=keyframes_result.get("keyframes", []),
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=callback_retry,
        )

        return {"status": "completed", "stage": "keyframe"}
