"""Verify all FASHION_FIXTURES entries pass Pydantic validation."""

import pytest
from src.services.llm_service import get_fashion_fixture, FASHION_FIXTURES
from src.schemas.ai_outputs import (
    FashionAssetAnalysis,
    ReferenceVideoAnalysis,
    CreativePlanResult,
    StoryboardResult,
    KeyframeGenerationResult,
    VideoClipGenerationResult,
    RepairResult,
)


class TestFashionFixtures:
    def test_fashion_asset_analysis_fixture(self):
        data = get_fashion_fixture("fashion_asset_analysis")
        result = FashionAssetAnalysis.model_validate(data)
        assert result.analysisText
        assert len(result.analyzedAssetIds) >= 1
        assert result.model

    def test_reference_video_analysis_fixture(self):
        data = get_fashion_fixture("reference_video_analysis")
        result = ReferenceVideoAnalysis.model_validate(data)
        assert len(result.shots) >= 1

    def test_fashion_plans_fixture(self):
        data = get_fashion_fixture("fashion_plans")
        result = CreativePlanResult.model_validate(data)
        assert len(result.plans) >= 1
        for plan in result.plans:
            assert plan.type
            assert plan.title
            assert plan.hook
            assert 0 <= plan.score <= 100

    def test_fashion_storyboard_fixture(self):
        data = get_fashion_fixture("fashion_storyboard")
        result = StoryboardResult.model_validate(data)
        assert result.title
        assert 15 <= result.duration <= 30
        assert len(result.shots) >= 4
        for shot in result.shots:
            assert shot.shotNo >= 1
            assert 1 <= shot.duration <= 8
            assert shot.subtitle

    def test_fake_keyframes_fixture(self):
        data = get_fashion_fixture("fake_keyframes")
        result = KeyframeGenerationResult.model_validate(data)
        assert len(result.keyframes) >= 1
        for kf in result.keyframes:
            assert kf.shotNo >= 1
            if kf.status == "completed":
                assert kf.url is not None
            if kf.status == "failed":
                assert kf.errorMessage is not None

    def test_fake_video_clips_fixture(self):
        data = get_fashion_fixture("fake_video_clips")
        result = VideoClipGenerationResult.model_validate(data)
        assert len(result.clips) >= 1
        for clip in result.clips:
            assert clip.shotNo >= 1
            if clip.status == "completed":
                assert clip.url is not None
                assert clip.duration is not None

    def test_feedback_classification_fixture(self):
        data = get_fashion_fixture("feedback_classification")
        result = RepairResult.model_validate(data)
        assert result.feedbackCategory
        assert result.targetType
        assert result.strategy
        assert len(result.affectedShots) >= 1

    def test_repair_plan_fixture(self):
        data = get_fashion_fixture("repair_plan")
        result = RepairResult.model_validate(data)
        assert result.strategy
        assert len(result.affectedShots) >= 1

    def test_all_fixtures_have_entries(self):
        """Ensure all expected fixture keys exist."""
        expected = {
            "fashion_asset_analysis", "reference_video_analysis",
            "fashion_plans", "fashion_storyboard",
            "keyframe_prompts", "fake_keyframes",
            "video_clip_prompts", "fake_video_clips",
            "feedback_classification", "repair_plan",
        }
        assert set(FASHION_FIXTURES.keys()) == expected

    def test_get_fashion_fixture_unknown(self):
        with pytest.raises(ValueError, match="No fashion fixture"):
            get_fashion_fixture("nonexistent")
