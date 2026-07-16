"""Integration tests for the callback payload contract between Python and Java.

These tests verify that the actual payloads Python sends to Java:
  1. Pass Pydantic validation (local contract check — always runs)
  2. Are accepted by the Java API (cross-service check — needs Java running)

This is the most fragile contract in the system because it crosses
the Python→Java boundary with 13 different payload shapes.
"""

import uuid

import pytest
from src.schemas.ai_outputs import CallbackPayload
from src.activities.callback_java import build_callback_payload, build_failed_callback_payload


class TestCallbackPayloadsLocal:
    """Fast contract tests — no external services needed.

    Every callback stage is tested with its required payload field.
    When you add a new callback stage, add a test here.
    """

    # ── Success payloads (one per callback stage) ─────────────────

    def test_asset_analysis_payload_passes_validation(self):
        payload = build_callback_payload(
            task_id=str(uuid.uuid4()),
            stage="asset_analysis",
            status="success",
            next_task_status="waiting_asset_confirmation",
            fashionAssetAnalysis={
                "analysisText": "Dress shown from the front",
                "analyzedAssetIds": ["a1"],
                "model": "vision-model",
                "analyzedAt": "2026-07-11T12:00:00Z",
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "asset_analysis"
        assert cb.fashionAssetAnalysis is not None

    def test_reference_analysis_payload_passes_validation(self):
        payload = build_callback_payload(
            task_id=str(uuid.uuid4()),
            stage="reference_analysis",
            status="success",
            next_task_status="plan_generating",
            referenceAnalysis={
                "shots": [{"shotNo": 1, "scene": "Hook", "action": "Walk"}],
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.stage == "reference_analysis"
        assert cb.referenceAnalysis is not None

    def test_creative_plan_payload_passes_validation(self, valid_creative_plan_payload):
        cb = CallbackPayload.model_validate(valid_creative_plan_payload)
        assert cb.plans is not None
        assert len(cb.plans) == 1

    def test_storyboard_payload_passes_validation(self, valid_storyboard_payload):
        cb = CallbackPayload.model_validate(valid_storyboard_payload)
        assert cb.storyboard is not None
        # storyboard is coerced to StoryboardResult (Pydantic model), not a dict
        assert len(cb.storyboard.shots) >= 4

    def test_keyframe_payload_passes_validation(self):
        payload = build_callback_payload(
            task_id=str(uuid.uuid4()),
            stage="keyframe",
            status="success",
            next_task_status="waiting_image_confirmation",
            keyframes=[
                {"shotNo": 1, "status": "completed", "url": "https://example.com/kf1.png",
                 "provider": "fake", "modelName": "fake-v1", "qualityScore": 85,
                 "source": "ai_generated", "imagePurpose": "first_frame"},
            ],
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.keyframes is not None
        assert len(cb.keyframes) == 1

    def test_video_clip_payload_passes_validation(self):
        payload = build_callback_payload(
            task_id=str(uuid.uuid4()),
            stage="video_clip",
            status="success",
            next_task_status="waiting_video_clip_confirmation",
            clips=[
                {"shotNo": 1, "status": "completed", "url": "https://example.com/clip1.mp4",
                 "provider": "fake", "modelName": "fake-v1", "duration": 3, "qualityScore": 85,
                 "source": "ai_generated"},
            ],
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.clips is not None
        assert len(cb.clips) == 1

    def test_repair_payload_passes_validation(self, valid_repair_payload):
        cb = CallbackPayload.model_validate(valid_repair_payload)
        assert cb.repairResult is not None
        assert cb.repairResult.feedbackCategory == "visual_quality"

    def test_qa_payload_passes_validation(self):
        payload = build_callback_payload(
            task_id=str(uuid.uuid4()),
            stage="qa",
            status="success",
            next_task_status="waiting_final_review",
            qaResult={
                "stage": "keyframe",
                "qualityScore": 90,
                "riskScore": 10,
                "checks": {
                    "productVisible": True,
                    "styleAccurate": True,
                    "lightingAcceptable": True,
                    "compositionValid": True,
                    "noArtifacts": True,
                },
                "suggestions": [],
                "complianceTips": [],
                "forbiddenClaims": [],
                "needsHumanReview": False,
            },
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.qaResult is not None

    # ── Failed payloads ───────────────────────────────────────────

    def test_failed_payload_passes_validation(self):
        payload = build_failed_callback_payload(
            task_id=str(uuid.uuid4()),
            stage="storyboard",
            error_code="STORYBOARD_GENERATION_FAILED",
            error_message="LLM timeout after 3 retries",
            retryable=True,
        )
        cb = CallbackPayload.model_validate(payload)
        assert cb.status == "failed"
        assert cb.error is not None
        assert cb.error.errorCode == "STORYBOARD_GENERATION_FAILED"
        assert cb.error.retryable is True
        assert cb.nextTaskStatus == "failed"

    # ── Contract enforcement ──────────────────────────────────────

    def test_stage_mismatch_rejected(self):
        """If stage='storyboard' but storyboard field is None, Pydantic rejects it."""
        payload = {
            "taskId": str(uuid.uuid4()),
            "schemaVersion": "1.0.0",
            "stage": "storyboard",
            "status": "success",
            "nextTaskStatus": "waiting_storyboard_confirmation",
            # Missing: storyboard field
        }
        with pytest.raises(Exception):  # Pydantic ValidationError
            CallbackPayload.model_validate(payload)

    def test_all_callback_stages_have_required_payload_field(self):
        """Every stage in the stage_payload_fields map has a test above."""
        from src.schemas.ai_outputs import CallbackStageEnum

        tested_stages = {
            "asset_analysis", "reference_analysis", "creative_plan",
            "storyboard", "keyframe", "video_clip", "qa", "repair",
        }

        for stage in CallbackStageEnum.__args__:
            if stage in ("render_manifest", "quality_check"):
                # RenderManifest callback is sent by Render Worker, not Python
                # quality_check is V1 only — tested in test_workflows.py
                continue
            if stage in ("product_analysis", "video_plan", "material"):
                # V1 legacy stages — tested in tests/test_workflows.py
                continue
            if stage not in tested_stages:
                pytest.fail(
                    f"Callback stage '{stage}' has no integration test. "
                    f"Add a test_callback_{stage}_passes_validation test here."
                )

    def test_repair_target_type_includes_all_db_values(self):
        """Regression test: RepairResult.targetType was missing render_manifest and final_video."""
        from src.schemas.ai_outputs import RepairResult

        payload = {
            "feedbackCategory": "visual_quality",
            "targetType": "final_video",
            "strategy": "regenerate_video_clip",
            "affectedShots": [1],
        }
        result = RepairResult.model_validate(payload)
        assert result.targetType == "final_video"

        payload["targetType"] = "render_manifest"
        result = RepairResult.model_validate(payload)
        assert result.targetType == "render_manifest"


class TestCallbackPayloadsAgainstJava:
    """Cross-service tests — need Java API running.

    These send actual HTTP POST requests to the Java callback endpoint.
    """

    @pytest.mark.asyncio
    async def test_java_accepts_valid_creative_plan_callback(self, api, valid_creative_plan_payload):
        """Java /api/ai-callbacks/{taskId} should accept a valid creative_plan payload."""
        task_id = valid_creative_plan_payload["taskId"]
        resp = await api.post(f"/api/ai-callbacks/{task_id}", json=valid_creative_plan_payload)

        # Java should return 200 for valid payloads
        # If 4xx, the payload doesn't match what Java expects
        assert resp.status_code in (200, 201, 404), (
            f"Expected 200/201/404, got {resp.status_code}: {resp.text[:300]}"
        )

    @pytest.mark.asyncio
    async def test_java_rejects_invalid_stage_payload(self, api):
        """Java should reject a callback with missing required field for its stage."""
        payload = {
            "taskId": str(uuid.uuid4()),
            "schemaVersion": "1.0.0",
            "stage": "storyboard",
            "status": "success",
            "nextTaskStatus": "waiting_storyboard_confirmation",
            # Missing: storyboard field
        }
        resp = await api.post(f"/api/ai-callbacks/{payload['taskId']}", json=payload)
        assert resp.status_code in (400, 422, 500), (
            f"Expected error for invalid payload, got {resp.status_code}"
        )

    @pytest.mark.asyncio
    async def test_java_rejects_unknown_callback_stage(self, api):
        """Java should reject a callback with a stage it doesn't know about."""
        payload = {
            "taskId": str(uuid.uuid4()),
            "schemaVersion": "1.0.0",
            "stage": "nonexistent_stage_xyz",
            "status": "success",
            "nextTaskStatus": "analyzing",
        }
        resp = await api.post(f"/api/ai-callbacks/{payload['taskId']}", json=payload)
        assert resp.status_code in (400, 422, 500), (
            f"Expected error for unknown stage, got {resp.status_code}"
        )
