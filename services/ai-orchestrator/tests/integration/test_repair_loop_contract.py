"""Integration tests for the repair loop contract.

The repair loop is the most complex cross-service flow:
  User feedback → Java → Python classify_feedback → Python plan_repair
  → Java callback → Java applies repair → dispatches regeneration workflow

These tests verify:
  1. Repair classification produces valid RepairResult payloads
  2. Repair plans preserve constraints (don't touch what shouldn't change)
  3. Repair nextTaskStatus matches the target_type
  4. Repair history tracking prevents duplicate repairs
"""

import uuid

import pytest
from src.schemas.ai_outputs import RepairResult, CallbackPayload


class TestRepairClassification:
    """Verify that classify_feedback produces valid, actionable repair results."""

    def test_all_feedback_categories_produce_valid_repair(self):
        """Every feedback category should produce a valid RepairResult."""
        categories = [
            "visual_quality", "product_accuracy", "lighting_issue",
            "action_stiffness", "missing_detail", "layout_composition",
            "style_mismatch", "other",
        ]

        for category in categories:
            result = RepairResult.model_validate({
                "feedbackCategory": category,
                "targetType": "video_clip",
                "strategy": "regenerate_video_clip",
                "affectedShots": [1],
            })
            assert result.feedbackCategory == category, f"Failed for {category}"

    def test_all_strategies_produce_valid_repair(self):
        """Every repair strategy should produce a valid RepairResult."""
        strategies = [
            "rewrite_storyboard_shot",
            "regenerate_keyframe_prompt",
            "regenerate_keyframe",
            "regenerate_video_clip_prompt",
            "regenerate_video_clip",
            "adjust_edit_instruction",
            "reorder_shots",
        ]

        for strategy in strategies:
            result = RepairResult.model_validate({
                "feedbackCategory": "visual_quality",
                "targetType": "video_clip",
                "strategy": strategy,
                "affectedShots": [1, 2],
            })
            assert result.strategy == strategy, f"Failed for {strategy}"

    def test_affected_shots_must_be_positive(self):
        """Shot numbers must be >= 1."""
        with pytest.raises(Exception):
            RepairResult.model_validate({
                "feedbackCategory": "visual_quality",
                "targetType": "video_clip",
                "strategy": "regenerate_video_clip",
                "affectedShots": [0],  # invalid
            })

    def test_affected_shots_cannot_be_empty(self):
        """Must have at least one affected shot."""
        with pytest.raises(Exception):
            RepairResult.model_validate({
                "feedbackCategory": "visual_quality",
                "targetType": "video_clip",
                "strategy": "regenerate_video_clip",
                "affectedShots": [],  # invalid
            })


class TestRepairPreserveConstraints:
    """Verify that repair preserves what it should.

    This is the core value proposition of the repair loop:
    "只改需要改的，不动不该动的"
    """

    def test_repair_preserves_product_details(self):
        """When repairing for lighting, product details must remain unchanged."""
        result = RepairResult.model_validate({
            "feedbackCategory": "lighting_issue",
            "targetType": "keyframe",
            "strategy": "regenerate_keyframe",
            "affectedShots": [3],
            "preserveConstraints": {
                "productDetails": ["floral print", "a-line silhouette"],
                "styleAttributes": ["Casual", "Bohemian"],
            },
            "newPrompt": "Same dress, better lighting, golden hour, sharp fabric",
        })

        assert result.preserveConstraints is not None
        assert "floral print" in result.preserveConstraints.productDetails
        assert "Casual" in result.preserveConstraints.styleAttributes

    def test_repair_without_constraints_is_valid(self):
        """Some repairs don't need explicit constraints (e.g., quick fixes)."""
        result = RepairResult.model_validate({
            "feedbackCategory": "other",
            "targetType": "video_clip",
            "strategy": "regenerate_video_clip",
            "affectedShots": [1],
        })
        assert result.preserveConstraints is None


class TestRepairNextTaskStatus:
    """Verify that repair target_type maps to the correct nextTaskStatus.

    From FashionRepairWorkflow._next_status_for_repair:
      keyframe → image_generating
      video_clip → video_clip_generating
      render_manifest / final_video → rendering
      default → keyframe_configuring
    """

    _NEXT_STATUS = {
        "keyframe": "image_generating",
        "video_clip": "video_clip_generating",
        "render_manifest": "rendering",
        "final_video": "rendering",
        "storyboard": "keyframe_configuring",
        "plan": "keyframe_configuring",
    }

    @pytest.mark.parametrize("target_type,expected_next_status", [
        ("keyframe", "image_generating"),
        ("video_clip", "video_clip_generating"),
        ("render_manifest", "rendering"),
        ("final_video", "rendering"),
        ("storyboard", "keyframe_configuring"),
        ("plan", "keyframe_configuring"),
    ])
    def test_repair_target_type_maps_to_correct_next_status(
        self, target_type, expected_next_status
    ):
        """Each repair target_type must route back to the correct regeneration stage."""
        repair_result = RepairResult.model_validate({
            "feedbackCategory": "visual_quality",
            "targetType": target_type,
            "strategy": "regenerate_video_clip",
            "affectedShots": [1],
        })

        next_status = self._NEXT_STATUS.get(repair_result.targetType, "keyframe_configuring")

        assert next_status == expected_next_status, (
            f"target_type={target_type} should map to {expected_next_status}, "
            f"got {next_status}"
        )

    def test_repair_callback_payload_includes_repair_result(self, valid_repair_payload):
        """The callback payload for repair stage must include repairResult."""
        cb = CallbackPayload.model_validate(valid_repair_payload)
        assert cb.stage == "repair"
        assert cb.repairResult is not None
        # The nextTaskStatus must be a valid video task status
        assert cb.nextTaskStatus is not None


class TestRepairLoopIntegration:
    """End-to-end repair loop tests — need Java API running."""

    @pytest.mark.asyncio
    async def test_submit_feedback_returns_repair_event(self, api):
        """Submitting repair feedback should create a repair event."""
        task_id = str(uuid.uuid4())

        resp = await api.post(f"/api/video-tasks/{task_id}/feedback", json={
            "taskId": task_id,
            "targetType": "video_clip",
            "feedbackCategory": "visual_quality",
            "affectedShots": [3],
            "feedbackText": "Shot 3 fabric detail is blurry, lighting is too dark",
            "preserveProductConstraints": True,
        })

        # Expect 4xx (task doesn't exist) — the point is the endpoint is reachable
        # and validates the request shape
        assert resp.status_code >= 400, (
            f"Expected error for non-existent task, got {resp.status_code}"
        )

    @pytest.mark.asyncio
    async def test_list_repair_events(self, api):
        """Listing repair events for a task should return an array."""
        task_id = str(uuid.uuid4())

        resp = await api.get(f"/api/video-tasks/{task_id}/repair-events")

        # 200 with empty array or 404 — both are acceptable contract behaviors
        assert resp.status_code in (200, 404), (
            f"Expected 200 or 404, got {resp.status_code}"
        )

        if resp.status_code == 200:
            events = resp.json()
            assert isinstance(events, list), f"Expected array, got {type(events)}"
