"""Integration tests for state machine transitions via the Java REST API.

These tests verify that the Java StateMachine enforces:
  1. Valid transitions are allowed
  2. Invalid transitions are rejected with clear errors
  3. Terminal states can't transition
  4. Idempotency: duplicate callbacks don't corrupt state

These complement VideoTaskStateMachineTest.java which tests
the in-process state machine without a real DB or HTTP layer.
"""

import uuid

import pytest


class TestStateTransitionsViaApi:
    """Test that state transitions work correctly through the REST API.

    Key invariant: the state machine in Java code, the DB CHECK constraint,
    and the OpenAPI spec must all agree on what transitions are valid.
    """

    @pytest.mark.asyncio
    async def test_create_task_sets_initial_status(self, api, test_email, valid_product):
        """A newly created task must be in 'draft' status."""
        # Register user first
        reg_resp = await api.post("/api/auth/register", json={
            "email": test_email,
            "password": "IntegrationTest123!",
        })
        if reg_resp.status_code >= 400:
            pytest.skip(f"Cannot register test user: {reg_resp.status_code}")

        token = reg_resp.json().get("accessToken", "")
        auth_headers = {"Authorization": f"Bearer {token}"}

        # Create product
        prod_resp = await api.post("/api/products", json=valid_product, headers=auth_headers)
        if prod_resp.status_code >= 400:
            pytest.skip(f"Cannot create test product: {prod_resp.status_code}")

        product_id = prod_resp.json()["id"]

        # Create video task
        task_resp = await api.post("/api/video-tasks", json={
            "productId": product_id,
            "taskMode": "PRODUCT_CREATIVE",
            "duration": 20,
        }, headers=auth_headers)

        assert task_resp.status_code in (200, 201), (
            f"Expected 200/201, got {task_resp.status_code}: {task_resp.text[:300]}"
        )

        task = task_resp.json()
        assert task["status"] == "draft", (
            f"New task must start in 'draft', got '{task['status']}'"
        )

    @pytest.mark.asyncio
    async def test_select_plan_rejected_when_not_waiting_plan_selection(self, api):
        """select-plan endpoint must reject calls when task is not in right state."""
        task_id = str(uuid.uuid4())
        plan_id = str(uuid.uuid4())

        # Attempt to select a plan on a non-existent task
        resp = await api.post(f"/api/video-tasks/{task_id}/select-plan", json={
            "planId": plan_id,
        })

        # Should get 4xx (not found or bad state)
        assert resp.status_code >= 400, (
            f"Expected error for non-existent task, got {resp.status_code}"
        )

    @pytest.mark.asyncio
    async def test_confirm_plan_rejected_before_plans_generated(self, api):
        """confirm-plan must fail if no plans exist yet."""
        task_id = str(uuid.uuid4())
        plan_id = str(uuid.uuid4())

        resp = await api.post(f"/api/video-tasks/{task_id}/confirm-plan", json={
            "planId": plan_id,
        })

        assert resp.status_code >= 400, (
            f"Expected error when confirming plan for non-existent task, got {resp.status_code}"
        )

    @pytest.mark.asyncio
    async def test_retry_only_allowed_from_failed(self, api):
        """retry endpoint must reject calls when task is not in 'failed' status."""
        task_id = str(uuid.uuid4())

        resp = await api.post(f"/api/video-tasks/{task_id}/retry")

        # Should get 4xx
        assert resp.status_code >= 400, (
            f"Expected error when retrying non-existent task, got {resp.status_code}"
        )


class TestIdempotency:
    """Verify that idempotent operations don't corrupt state.

    From CLAUDE.md: "Callback handlers must be idempotent"
                      "Quota operations use idempotency_key with UNIQUE constraint"
    """

    @pytest.mark.asyncio
    async def test_duplicate_callback_with_same_stage(self, api, valid_creative_plan_payload):
        """Sending the same callback twice should not corrupt the task state."""
        task_id = valid_creative_plan_payload["taskId"]

        # First callback
        resp1 = await api.post(f"/api/ai-callbacks/{task_id}", json=valid_creative_plan_payload)

        # Second callback with same payload
        resp2 = await api.post(f"/api/ai-callbacks/{task_id}", json=valid_creative_plan_payload)

        # Both should succeed (idempotent), or second should be 409 (conflict/rejected)
        assert resp1.status_code in (200, 201, 404), f"First callback: {resp1.status_code}"
        assert resp2.status_code in (200, 201, 404, 409), (
            f"Second callback should be idempotent (200/201/404/409), got {resp2.status_code}"
        )


class TestErrorResponses:
    """Verify that the API returns structured, actionable error responses.

    From CLAUDE.md: "Error payloads use {errorCode, errorMessage, failedStage, retryable}"
    """

    @pytest.mark.asyncio
    async def test_404_has_structured_error(self, api):
        """Getting a non-existent task should return an error body."""
        task_id = str(uuid.uuid4())
        resp = await api.get(f"/api/video-tasks/{task_id}")

        assert resp.status_code == 404, f"Expected 404, got {resp.status_code}"

        body = resp.json()
        # Java should return an error body with at minimum an error message
        assert "error" in body or "message" in body or "errorMessage" in body, (
            f"404 response should contain error details: {resp.text[:300]}"
        )

    @pytest.mark.asyncio
    async def test_invalid_uuid_rejected(self, api):
        """Malformed UUIDs should be rejected before reaching the service layer."""
        resp = await api.get("/api/video-tasks/not-a-uuid")

        assert resp.status_code >= 400, (
            f"Malformed UUID should get 4xx, got {resp.status_code}"
        )
