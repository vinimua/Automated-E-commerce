"""Verify callback payloads match Java AiCallbackRequest expectations.

Tests that the build_callback_payload and build_failed_callback_payload helpers
produce dicts that pass CallbackPayload validation for every stage.
"""

import pytest
from src.activities.callback_java import build_callback_payload, build_failed_callback_payload
from src.schemas.ai_outputs import CallbackPayload


class TestBuildCallbackPayload:
    """Test the build_callback_payload helper for every Fashion Creative Loop stage."""

    TASK_ID = "550e8400-e29b-41d4-a716-446655440000"

    def test_asset_analysis_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "asset_analysis", "success", "waiting_asset_confirmation",
            fashionAssetAnalysis={
                "productCategory": "Dresses",
                "styleAttributes": ["Casual"],
                "recommendedAngles": ["Front"],
                "assetQualityScore": 80,
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "asset_analysis"
        assert cb.status == "success"
        assert cb.nextTaskStatus == "waiting_asset_confirmation"
        assert cb.fashionAssetAnalysis is not None

    def test_reference_analysis_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "reference_analysis", "success", "plan_generating",
            referenceAnalysis={
                "shots": [{"shotNo": 1, "scene": "Test", "action": "Test"}],
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "reference_analysis"
        assert cb.nextTaskStatus == "plan_generating"
        assert cb.referenceAnalysis is not None

    def test_creative_plan_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "creative_plan", "success", "waiting_plan_selection",
            plans=[
                {
                    "type": "pain_point_solution",
                    "title": "Test Plan",
                    "hook": "Test hook",
                    "structure": "Pain → Product",
                    "reason": "Best",
                    "estimatedDuration": 20,
                    "score": 85,
                },
            ],
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "creative_plan"
        assert cb.plans is not None
        assert len(cb.plans) == 1

    def test_storyboard_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "storyboard", "success", "waiting_storyboard_confirmation",
            storyboard={
                "title": "Test",
                "hook": "Hook",
                "duration": 20,
                "caption": "Caption",
                "hashtags": ["#test"],
                "coverText": "Cover",
                "musicSuggestion": "Upbeat",
                "shots": [
                    {
                        "shotNo": 1, "duration": 3,
                        "scene": "Scene", "action": "Action",
                        "subtitle": "Sub", "materialType": "ai_video",
                        "prompt": "Prompt", "editInstruction": "Edit",
                    },
                ],
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "storyboard"
        assert cb.storyboard is not None

    def test_keyframe_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "keyframe", "success", "waiting_image_confirmation",
            keyframes=[
                {"shotNo": 1, "status": "completed", "url": "https://x.com/k.png", "provider": "fake"},
            ],
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "keyframe"
        assert cb.keyframes is not None
        assert len(cb.keyframes) == 1

    def test_video_clip_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "video_clip", "success", "waiting_video_clip_confirmation",
            clips=[
                {"shotNo": 1, "status": "completed", "url": "https://x.com/c.mp4", "duration": 3, "provider": "fake"},
            ],
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "video_clip"
        assert cb.clips is not None
        assert len(cb.clips) == 1

    def test_repair_success(self):
        payload = build_callback_payload(
            self.TASK_ID, "repair", "success", "video_clip_generating",
            repairResult={
                "repairEventId": "550e8400-e29b-41d4-a716-446655440010",
                "feedbackCategory": "visual_quality",
                "targetType": "video_clip",
                "strategy": "regenerate_video_clip",
                "affectedShots": [3],
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "repair"
        assert cb.repairResult is not None

    def test_all_legacy_stages(self):
        """Verify legacy V1 stages still work."""
        stages = [
            ("product_analysis", "analysis_completed"),
            ("video_plan", "plan_generated"),
            ("material", "material_generated"),
            ("quality_check", "checking"),
            ("render_manifest", "rendering"),
        ]
        for stage, next_status in stages:
            payload = build_callback_payload(
                self.TASK_ID, stage, "success", next_status,
                **{_stage_data_key(stage): _minimal_data(stage)},
            )
            cb = CallbackPayload.model_validate(payload)
            assert cb.stage == stage
            assert cb.status == "success"

    def test_failed_payload(self):
        payload = build_failed_callback_payload(
            self.TASK_ID, "keyframe", "GEN_FAILED", "Image generation timed out", True,
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.status == "failed"
        assert cb.error is not None
        assert cb.error.errorCode == "GEN_FAILED"
        assert cb.error.retryable is True


def _stage_data_key(stage: str) -> str:
    """Map stage to the CallbackPayload field name."""
    mapping = {
        "product_analysis": "productAnalysis",
        "video_plan": "plans",
        "material": "materials",
        "quality_check": "qualityCheck",
        "render_manifest": "renderManifest",
    }
    return mapping[stage]


def _minimal_data(stage: str):
    """Return minimal valid data for each stage."""
    if stage == "product_analysis":
        return {"category": "Test", "sellingPoints": ["Good"], "painPoints": ["Bad"],
                "targetAudience": ["All"], "scenes": ["Home"], "recommendedVideoTypes": ["review"],
                "videoScore": 70, "riskTips": []}
    if stage == "video_plan":
        return [{"type": "review", "title": "T", "hook": "H", "structure": "S", "reason": "R",
                 "estimatedDuration": 20, "score": 80}]
    if stage == "material":
        return [{"shotNo": 1, "type": "image", "status": "completed", "url": "https://x.com/m.png", "provider": "fake"}]
    if stage == "quality_check":
        return {"qualityScore": 80, "riskScore": 10,
                "checks": {"hasHook": True, "productAppearsEarly": True, "subtitleReadable": True,
                           "noSensitiveClaims": True, "visualQualityAcceptable": True}}
    if stage == "render_manifest":
        return {"manifestVersion": "1.0.0", "taskId": "550e8400-e29b-41d4-a716-446655440000",
                "videoId": "550e8400-e29b-41d4-a716-446655440001", "videoType": "review",
                "template": "review_v1", "resolution": "1080x1920", "fps": 30, "duration": 15,
                "assets": [], "subtitleStyle": {"fontSize": 24, "position": "bottom_center", "background": "none"},
                "output": {"format": "mp4", "codec": "h264"}}
    return {}
