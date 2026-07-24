"""FashionVideoClipWorkflow: generate prompts → fake_generate_video_clips → callback_java."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import generate_video_clip_prompts, fake_generate_video_clips, callback_java
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class FashionVideoClipWorkflow:
    """Generate video clips from keyframes, then callback to Java.

    Flow:
    1. generate_video_clip_prompts → compile prompts enriched with keyframe info
    2. fake_generate_video_clips → generate placeholder videos (or real in M6)
    3. callback_java: stage="video_clip", nextTaskStatus="waiting_video_clip_confirmation"
    """

    @workflow.run
    async def run(self, task_id: str, storyboard: dict, keyframes: dict) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        callback_retry = RetryPolicy(maximum_attempts=3)

        # Step 1: Generate video clip prompts
        try:
            prompts = await workflow.execute_activity(
                "generate_video_clip_prompts",
                args=[task_id, storyboard, keyframes],
                start_to_close_timeout=timedelta(minutes=2),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "video_clip", "CLIP_PROMPT_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "video_clip", "error": str(e)}

        # Step 2: Generate video clips (fake or real)
        try:
            clips_result = await workflow.execute_activity(
                "fake_generate_video_clips",
                args=[task_id, prompts, storyboard, keyframes],
                start_to_close_timeout=timedelta(minutes=10),
                retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "video_clip", "VIDEO_CLIP_GENERATION_FAILED", str(e), True),
                start_to_close_timeout=timedelta(seconds=30),
                retry_policy=callback_retry,
            )
            return {"status": "failed", "stage": "video_clip", "error": str(e)}

        # Step 3: Callback to Java
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(
                task_id, "video_clip", "success", "waiting_video_clip_confirmation",
                clips=clips_result.get("clips", []),
            ),
            start_to_close_timeout=timedelta(seconds=30),
            retry_policy=callback_retry,
        )

        return {"status": "completed", "stage": "video_clip"}
