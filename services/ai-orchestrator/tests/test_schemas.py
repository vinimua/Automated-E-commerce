"""Validate all Pydantic schemas with sample data."""

import pytest
from src.schemas.ai_outputs import (
    FashionAssetAnalysis,
    ReferenceVideoAnalysis,
    CreativePlanResult,
    CreativePlanItem,
    KeyframeGenerationResult,
    KeyframeItem,
    VideoClipGenerationResult,
    VideoClipItem,
    RepairResult,
    FashionQaResult,
    FashionQaChecks,
    CallbackPayload,
    CallbackError,
)
from src.schemas.workflow_requests import (
    AssetAnalysisRequest,
    ReferenceAnalysisRequest,
    CreativePlanRequest,
    StoryboardGenerationRequest,
    KeyframeGenerationRequest,
    VideoClipGenerationRequest,
    RepairRequest,
    WorkflowTriggerResponse,
)


class TestFashionAssetAnalysis:
    def test_valid_minimal(self):
        data = {
            "productCategory": "Dresses",
            "styleAttributes": ["Casual", "Summer"],
            "visualFeatures": {},
            "recommendedAngles": ["Front"],
            "assetQualityScore": 75,
        }
        result = FashionAssetAnalysis.model_validate(data)
        assert result.productCategory == "Dresses"
        assert result.assetQualityScore == 75

    def test_valid_full(self):
        data = {
            "productCategory": "Women's Summer Dress",
            "styleAttributes": ["Casual", "Bohemian", "Floral"],
            "visualFeatures": {
                "colors": ["White", "Blue"],
                "patterns": ["Floral"],
                "materials": ["Cotton"],
                "fit": "A-line",
                "occasions": ["Beach", "Brunch"],
            },
            "recommendedAngles": ["Front full-body", "Back detail", "Fabric close-up"],
            "assetQualityScore": 82,
            "missingAngles": ["360-degree spin"],
            "lightingNotes": "Natural daylight recommended",
            "backgroundRecommendations": ["Beach", "Garden"],
            "modelRequirements": "Size S model",
        }
        result = FashionAssetAnalysis.model_validate(data)
        assert len(result.recommendedAngles) == 3

    def test_missing_required(self):
        with pytest.raises(Exception):
            FashionAssetAnalysis.model_validate({"productCategory": "Dresses"})

    def test_score_range(self):
        data = {
            "productCategory": "Dresses",
            "styleAttributes": ["Casual"],
            "recommendedAngles": ["Front"],
            "assetQualityScore": 150,
        }
        with pytest.raises(Exception):
            FashionAssetAnalysis.model_validate(data)


class TestReferenceVideoAnalysis:
    def test_valid(self):
        data = {
            "title": "Summer BTS",
            "duration": 28.5,
            "shots": [
                {
                    "shotNo": 1,
                    "scene": "Model walks in",
                    "action": "Walk toward camera",
                }
            ],
        }
        result = ReferenceVideoAnalysis.model_validate(data)
        assert result.title == "Summer BTS"
        assert len(result.shots) == 1

    def test_shots_min_length(self):
        with pytest.raises(Exception):
            ReferenceVideoAnalysis.model_validate({"shots": []})


class TestCreativePlanResult:
    def test_valid(self):
        data = {
            "plans": [
                {
                    "type": "pain_point_solution",
                    "title": "Test Plan",
                    "hook": "Test hook",
                    "structure": "Pain → Solution",
                    "reason": "Best fit",
                    "estimatedDuration": 20,
                    "score": 85,
                }
            ]
        }
        result = CreativePlanResult.model_validate(data)
        assert len(result.plans) == 1
        assert isinstance(result.plans[0], CreativePlanItem)

    def test_empty_plans(self):
        with pytest.raises(Exception):
            CreativePlanResult.model_validate({"plans": []})


class TestKeyframeGenerationResult:
    def test_valid_completed(self):
        data = {
            "keyframes": [
                {
                    "shotNo": 1,
                    "status": "completed",
                    "url": "https://example.com/kf.png",
                    "provider": "fake",
                }
            ]
        }
        result = KeyframeGenerationResult.model_validate(data)
        assert result.keyframes[0].url == "https://example.com/kf.png"

    def test_completed_requires_url(self):
        data = {"keyframes": [{"shotNo": 1, "status": "completed"}]}
        with pytest.raises(Exception):
            KeyframeGenerationResult.model_validate(data)

    def test_failed_requires_error_message(self):
        data = {"keyframes": [{"shotNo": 1, "status": "failed"}]}
        with pytest.raises(Exception):
            KeyframeGenerationResult.model_validate(data)

    def test_failed_with_error(self):
        data = {
            "keyframes": [
                {"shotNo": 1, "status": "failed", "errorMessage": "Generation timed out"}
            ]
        }
        result = KeyframeGenerationResult.model_validate(data)
        assert result.keyframes[0].errorMessage == "Generation timed out"


class TestVideoClipGenerationResult:
    def test_valid_completed(self):
        data = {
            "clips": [
                {
                    "shotNo": 1,
                    "status": "completed",
                    "url": "https://example.com/clip.mp4",
                    "duration": 3,
                    "provider": "fake",
                }
            ]
        }
        result = VideoClipGenerationResult.model_validate(data)
        assert result.clips[0].duration == 3

    def test_completed_requires_duration(self):
        data = {"clips": [{"shotNo": 1, "status": "completed", "url": "https://example.com/c.mp4"}]}
        with pytest.raises(Exception):
            VideoClipGenerationResult.model_validate(data)


class TestRepairResult:
    def test_valid(self):
        data = {
            "feedbackCategory": "visual_quality",
            "targetType": "video_clip",
            "strategy": "regenerate_video_clip",
            "affectedShots": [3],
        }
        result = RepairResult.model_validate(data)
        assert result.strategy == "regenerate_video_clip"
        assert 3 in result.affectedShots

    def test_invalid_shot_number(self):
        data = {
            "feedbackCategory": "visual_quality",
            "targetType": "keyframe",
            "strategy": "regenerate_keyframe",
            "affectedShots": [0],
        }
        with pytest.raises(Exception):
            RepairResult.model_validate(data)

    def test_invalid_category(self):
        data = {
            "feedbackCategory": "invalid_category",
            "targetType": "keyframe",
            "strategy": "regenerate_keyframe",
            "affectedShots": [1],
        }
        with pytest.raises(Exception):
            RepairResult.model_validate(data)


class TestFashionQaResult:
    def test_valid(self):
        data = {
            "stage": "keyframe",
            "qualityScore": 85,
            "checks": {
                "productVisible": True,
                "styleAccurate": True,
                "lightingAcceptable": True,
                "compositionValid": True,
                "noArtifacts": True,
            },
            "needsHumanReview": False,
        }
        result = FashionQaResult.model_validate(data)
        assert result.qualityScore == 85
        assert result.checks.productVisible is True


class TestCallbackPayload:
    def test_success_asset_analysis(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "stage": "asset_analysis",
            "status": "success",
            "nextTaskStatus": "waiting_asset_confirmation",
            "fashionAssetAnalysis": {
                "productCategory": "Dresses",
                "styleAttributes": ["Casual"],
                "recommendedAngles": ["Front"],
                "assetQualityScore": 80,
            },
        }
        result = CallbackPayload.model_validate(data)
        assert result.status == "success"

    def test_success_keyframe(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "stage": "keyframe",
            "status": "success",
            "nextTaskStatus": "waiting_image_confirmation",
            "keyframes": [
                {"shotNo": 1, "status": "completed", "url": "https://x.com/k.png", "provider": "fake"}
            ],
        }
        result = CallbackPayload.model_validate(data)
        assert result.keyframes is not None

    def test_failed_requires_error(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "stage": "keyframe",
            "status": "failed",
        }
        with pytest.raises(Exception):
            CallbackPayload.model_validate(data)

    def test_failed_with_error(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "stage": "keyframe",
            "status": "failed",
            "nextTaskStatus": "failed",
            "error": {
                "errorCode": "GEN_FAILED",
                "errorMessage": "Generation failed",
                "failedStage": "keyframe",
                "retryable": True,
            },
        }
        result = CallbackPayload.model_validate(data)
        assert result.error.errorCode == "GEN_FAILED"

    def test_missing_stage_payload(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "stage": "keyframe",
            "status": "success",
            "nextTaskStatus": "waiting_image_confirmation",
        }
        with pytest.raises(Exception):
            CallbackPayload.model_validate(data)


class TestWorkflowRequests:
    def test_asset_analysis_request(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "productId": "550e8400-e29b-41d4-a716-446655440001",
            "userId": "550e8400-e29b-41d4-a716-446655440002",
            "correlationId": "test-correlation",
            "productContext": {"name": "Test"},
            "assets": [{"url": "https://example.com/img.jpg", "role": "product_front"}],
        }
        result = AssetAnalysisRequest.model_validate(data)
        assert result.taskId is not None

    def test_repair_request(self):
        data = {
            "taskId": "550e8400-e29b-41d4-a716-446655440000",
            "productId": "550e8400-e29b-41d4-a716-446655440001",
            "userId": "550e8400-e29b-41d4-a716-446655440002",
            "correlationId": "test-correlation",
            "feedbackText": "The fabric looks blurry in shot 3",
            "category": "visual_quality",
            "targetType": "video_clip",
        }
        result = RepairRequest.model_validate(data)
        assert result.feedbackText == "The fabric looks blurry in shot 3"

    def test_workflow_response(self):
        data = {"workflow_id": "wf-123", "status": "started", "message": "OK"}
        result = WorkflowTriggerResponse.model_validate(data)
        assert result.status == "started"
