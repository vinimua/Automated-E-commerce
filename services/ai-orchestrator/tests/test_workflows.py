"""End-to-end workflow tests using fixtures (no Temporal server needed).

Tests that workflow activities produce valid callback payloads by running
the activity functions directly (not through Temporal).
"""

import pytest
from src.activities.analyze_fashion_assets import analyze_fashion_assets
from src.activities.analyze_reference_video import analyze_reference_video
from src.activities.generate_fashion_plans import generate_fashion_plans
from src.activities.generate_fashion_storyboard import generate_fashion_storyboard
from src.activities.generate_keyframe_prompts import generate_keyframe_prompts
from src.activities.fake_generate_keyframes import fake_generate_keyframes
from src.activities.generate_video_clip_prompts import generate_video_clip_prompts
from src.activities.fake_generate_video_clips import fake_generate_video_clips
from src.activities.classify_feedback import classify_feedback
from src.activities.plan_repair import plan_repair
from src.activities.callback_java import build_callback_payload, build_failed_callback_payload
from src.schemas.ai_outputs import (
    FashionAssetAnalysis,
    ReferenceVideoAnalysis,
    CreativePlanResult,
    StoryboardResult,
    KeyframeGenerationResult,
    VideoClipGenerationResult,
    RepairResult,
    CallbackPayload,
)


@pytest.mark.asyncio
class TestFashionActivities:
    """Test each activity function returns schema-valid data."""

    async def test_analyze_fashion_assets(self, sample_product_context):
        result = await analyze_fashion_assets("test-task-id", sample_product_context)
        validated = FashionAssetAnalysis.model_validate(result)
        assert validated.analysisText
        assert len(validated.analyzedAssetIds) >= 1

    async def test_analyze_reference_video(self):
        result = await analyze_reference_video("test-task-id", "https://example.com/ref.mp4")
        validated = ReferenceVideoAnalysis.model_validate(result)
        assert len(validated.shots) >= 1

    async def test_generate_fashion_plans(self, sample_product_context):
        creative_context = {"productProfile": sample_product_context, "assetAnalysis": {"summary": "Dress"}}
        result = await generate_fashion_plans("test-task-id", creative_context)
        validated = CreativePlanResult.model_validate(result)
        assert len(validated.plans) >= 1
        # Each plan should have required fields
        for plan in validated.plans:
            assert plan.type
            assert plan.title

    async def test_generate_fashion_storyboard(self, sample_product_context, sample_plan):
        result = await generate_fashion_storyboard(
            "test-task-id", sample_plan, sample_product_context, 20, "pain_point_solution"
        )
        validated = StoryboardResult.model_validate(result)
        assert len(validated.shots) >= 4
        assert 15 <= validated.duration <= 30

    async def test_generate_keyframe_prompts(self, sample_storyboard):
        result = await generate_keyframe_prompts("test-task-id", sample_storyboard)
        assert "prompts" in result
        assert len(result["prompts"]) >= 1  # fake mode returns fixture prompts

    async def test_fake_generate_keyframes(self, sample_storyboard):
        prompts = {"prompts": [{"shotNo": 1, "prompt": "Test", "negativePrompt": "blurry"}]}
        result = await fake_generate_keyframes("test-task-id", prompts, sample_storyboard)
        validated = KeyframeGenerationResult.model_validate(result)
        assert len(validated.keyframes) >= 1

    async def test_generate_video_clip_prompts(self, sample_storyboard, sample_keyframes):
        result = await generate_video_clip_prompts("test-task-id", sample_storyboard, sample_keyframes)
        assert "prompts" in result
        assert len(result["prompts"]) >= 1  # fake mode returns fixture prompts

    async def test_fake_generate_video_clips(self, sample_storyboard):
        prompts = {"prompts": [{"shotNo": 1, "prompt": "Test", "negativePrompt": "blurry", "duration": 3}]}
        result = await fake_generate_video_clips("test-task-id", prompts, sample_storyboard)
        validated = VideoClipGenerationResult.model_validate(result)
        assert len(validated.clips) >= 1

    async def test_classify_feedback(self):
        result = await classify_feedback(
            "test-task-id",
            "The fabric looks blurry in shot 3",
            "visual_quality",
            "video_clip",
        )
        validated = RepairResult.model_validate(result)
        assert validated.feedbackCategory
        assert validated.targetType
        assert validated.strategy

    async def test_plan_repair(self):
        classification = {
            "feedbackCategory": "visual_quality",
            "targetType": "video_clip",
            "strategy": "regenerate_video_clip",
            "affectedShots": [3],
            "repairNotes": "Test",
        }
        result = await plan_repair("test-task-id", classification, {})
        validated = RepairResult.model_validate(result)
        assert validated.strategy
        assert len(validated.affectedShots) >= 1


class TestFashionWorkflowEndToEnd:
    """Simulate full workflow chains by calling activities in sequence."""

    TASK_ID = "550e8400-e29b-41d4-a716-446655440000"

    @pytest.mark.asyncio
    async def test_asset_analysis_flow(self, sample_product_context):
        """Full asset analysis workflow: analyze → build callback → validate."""
        analysis = await analyze_fashion_assets(self.TASK_ID, sample_product_context)
        payload = build_callback_payload(
            self.TASK_ID, "asset_analysis", "success", "waiting_asset_confirmation",
            fashionAssetAnalysis=analysis,
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.fashionAssetAnalysis is not None

    @pytest.mark.asyncio
    async def test_plan_to_storyboard_flow(self, sample_product_context, sample_plan):
        """Plan → Storyboard → validate callback chain."""
        creative_context = {"productProfile": sample_product_context, "assetAnalysis": {}}
        plan = await generate_fashion_plans(self.TASK_ID, creative_context)
        plan_payload = build_callback_payload(
            self.TASK_ID, "creative_plan", "success", "waiting_plan_selection",
            plans=plan["plans"],
        )
        CallbackPayload.model_validate(plan_payload)

        storyboard = await generate_fashion_storyboard(
            self.TASK_ID, sample_plan, sample_product_context, 20, "pain_point_solution"
        )
        sb_payload = build_callback_payload(
            self.TASK_ID, "storyboard", "success", "waiting_storyboard_confirmation",
            storyboard=storyboard,
        )
        CallbackPayload.model_validate(sb_payload)

    @pytest.mark.asyncio
    async def test_keyframe_to_clip_flow(self, sample_storyboard):
        """Keyframe prompts → fake generate → clip prompts → fake generate → validate."""
        # Keyframe generation
        kf_prompts = await generate_keyframe_prompts(self.TASK_ID, sample_storyboard)
        keyframes = await fake_generate_keyframes(self.TASK_ID, kf_prompts, sample_storyboard)
        kf_payload = build_callback_payload(
            self.TASK_ID, "keyframe", "success", "waiting_image_confirmation",
            keyframes=keyframes["keyframes"],
        )
        CallbackPayload.model_validate(kf_payload)

        # Video clip generation
        clip_prompts = await generate_video_clip_prompts(self.TASK_ID, sample_storyboard, keyframes)
        clips = await fake_generate_video_clips(self.TASK_ID, clip_prompts, sample_storyboard)
        clip_payload = build_callback_payload(
            self.TASK_ID, "video_clip", "success", "waiting_video_clip_confirmation",
            clips=clips["clips"],
        )
        CallbackPayload.model_validate(clip_payload)

    @pytest.mark.asyncio
    async def test_repair_flow(self):
        """Feedback → classify → plan → validate callback chain."""
        classification = await classify_feedback(
            self.TASK_ID, "Shot 3 is blurry", "visual_quality", "video_clip"
        )
        repair_plan_result = await plan_repair(
            self.TASK_ID,
            classification,
            {"status": "repairing", "repairEventId": "550e8400-e29b-41d4-a716-446655440010"},
        )
        repair_plan_result = {
            **repair_plan_result,
            "repairEventId": "550e8400-e29b-41d4-a716-446655440010",
        }
        repair_payload = build_callback_payload(
            self.TASK_ID, "repair", "success", "video_clip_generating",
            repairResult=repair_plan_result,
        )
        cb = CallbackPayload.model_validate(repair_payload)
        assert cb.repairResult is not None

    def test_failed_callback_all_stages(self):
        """Verify failed callbacks work for all Fashion Creative Loop stages."""
        fashion_stages = [
            "asset_analysis", "reference_analysis", "creative_plan",
            "storyboard", "keyframe", "video_clip", "repair",
        ]
        for stage in fashion_stages:
            payload = build_failed_callback_payload(
                self.TASK_ID, stage, "TEST_ERROR", f"Simulated failure in {stage}", True,
            )
            cb = CallbackPayload.model_validate(payload)
            assert cb.status == "failed"
            assert cb.error is not None
            assert cb.error.failedStage == stage
