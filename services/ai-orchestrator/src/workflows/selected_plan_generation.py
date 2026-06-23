"""SelectedPlanGenerationWorkflow: 8 activities from script to render manifest."""

from datetime import timedelta
from temporalio import workflow
from temporalio.common import RetryPolicy

with workflow.unsafe.imports_passed_through():
    from src.activities import (
        generate_script, generate_storyboard, generate_asset_prompts,
        generate_image_assets, generate_video_clips, check_asset_quality,
        build_render_manifest, callback_java,
    )
    from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


@workflow.defn
class SelectedPlanGenerationWorkflow:
    @workflow.run
    async def run(
        self,
        task_id: str,
        product_context: dict,
        plan: dict,
        duration: int,
        video_type: str,
    ) -> dict:
        retry = RetryPolicy(
            maximum_attempts=3,
            initial_interval=timedelta(seconds=2),
            maximum_interval=timedelta(seconds=30),
        )
        timeout_2m = timedelta(minutes=2)
        timeout_30s = timedelta(seconds=30)

        # 1. Generate script
        try:
            script = await workflow.execute_activity(
                "generate_script",
                args=[plan, product_context],
                start_to_close_timeout=timeout_2m, retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "storyboard", "AI_SCRIPT_FAILED", str(e), True),
                start_to_close_timeout=timeout_30s, retry_policy=RetryPolicy(maximum_attempts=3),
            )
            return {"status": "failed", "stage": "script"}

        # 2. Generate storyboard
        try:
            storyboard = await workflow.execute_activity(
                "generate_storyboard",
                args=[script, plan, product_context, duration, video_type],
                start_to_close_timeout=timeout_2m, retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "storyboard", "AI_STORYBOARD_FAILED", str(e), True),
                start_to_close_timeout=timeout_30s,
            )
            return {"status": "failed", "stage": "storyboard"}

        # 3. Callback: storyboard ready → Java advances to script_generated
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(task_id, "storyboard", "success", "script_generated", storyboard=storyboard),
            start_to_close_timeout=timeout_30s, retry_policy=RetryPolicy(maximum_attempts=3),
        )

        # 4. Generate asset prompts (pass-through from storyboard)
        prompts = await workflow.execute_activity(
            "generate_asset_prompts", storyboard,
            start_to_close_timeout=timeout_30s, retry_policy=retry,
        )

        # 5. Generate image assets
        try:
            image_assets = await workflow.execute_activity(
                "generate_image_assets", prompts,
                start_to_close_timeout=timedelta(minutes=5), retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "material", "AI_IMAGE_FAILED", str(e), True),
                start_to_close_timeout=timeout_30s,
            )
            return {"status": "failed", "stage": "image_assets"}

        # 6. Generate video clips
        try:
            video_assets = await workflow.execute_activity(
                "generate_video_clips",
                args=[prompts, image_assets],
                start_to_close_timeout=timedelta(minutes=10), retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "material", "AI_VIDEO_FAILED", str(e), True),
                start_to_close_timeout=timeout_30s,
            )
            return {"status": "failed", "stage": "video_clips"}

        # Combine materials from both image and video generation
        combined_materials = {
            "materials": (
                image_assets.get("materials", []) + video_assets.get("materials", [])
            )
        }

        # Callback: materials ready → Java advances to material_generated
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(task_id, "material", "success", "material_generated", materials=combined_materials.get("materials")),
            start_to_close_timeout=timeout_30s,
        )

        # 7. Quality check
        try:
            quality = await workflow.execute_activity(
                "check_asset_quality",
                args=[prompts, combined_materials],
                start_to_close_timeout=timeout_2m, retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "quality_check", "AI_QUALITY_FAILED", str(e), True),
                start_to_close_timeout=timeout_30s,
            )
            return {"status": "failed", "stage": "quality_check"}

        # Callback: quality check done
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(task_id, "quality_check", "success", None, qualityCheck=quality),
            start_to_close_timeout=timeout_30s,
        )

        # 8. Build render manifest
        try:
            manifest = await workflow.execute_activity(
                "build_render_manifest",
                args=[task_id, video_type, storyboard, combined_materials],
                start_to_close_timeout=timeout_30s, retry_policy=retry,
            )
        except Exception as e:
            await workflow.execute_activity(
                "callback_java",
                build_failed_callback_payload(task_id, "render_manifest", "AI_MANIFEST_FAILED", str(e), True),
                start_to_close_timeout=timeout_30s,
            )
            return {"status": "failed", "stage": "render_manifest"}

        # Callback: render manifest ready → Java pushes to RabbitMQ
        await workflow.execute_activity(
            "callback_java",
            build_callback_payload(task_id, "render_manifest", "success", "rendering", renderManifest=manifest),
            start_to_close_timeout=timeout_30s,
        )

        return {"status": "completed", "stage": "render_manifest"}
