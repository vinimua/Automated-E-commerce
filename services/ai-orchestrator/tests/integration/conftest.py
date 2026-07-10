"""Integration test fixtures.

Tests in this directory verify cross-service contracts:
  - Java API ↔ Python callback payloads
  - State machine transitions via REST API
  - Repair loop end-to-end

When JAVA_API_URL is reachable, tests run against the real Java API.
When unreachable (e.g. local dev without Java running), integration tests are skipped.

Usage:
  # Run only integration tests
  pytest tests/integration/ -v

  # Point to a specific Java instance
  JAVA_API_URL=http://localhost:8080 pytest tests/integration/ -v

  # Run with docker compose in CI
  JAVA_API_URL=http://java-api:8080 pytest tests/integration/ -v
"""

import os
import uuid

import httpx
import pytest


# ── Configuration ─────────────────────────────────────────────────

JAVA_API_URL = os.getenv("JAVA_API_URL", "http://localhost:8080")
INTERNAL_TOKEN = os.getenv("INTERNAL_SERVICE_TOKEN", "internal-dev-token-change-in-production")


def is_java_api_reachable() -> bool:
    """Check if Java API is available for integration testing."""
    try:
        resp = httpx.get(f"{JAVA_API_URL}/health", timeout=3)
        return resp.status_code == 200
    except Exception:
        return False


# ── Async HTTP client ─────────────────────────────────────────────

@pytest.fixture
async def api():
    """HTTP client pointed at the Java API."""
    if not is_java_api_reachable():
        pytest.skip(f"Java API not reachable at {JAVA_API_URL}")
    async with httpx.AsyncClient(
        base_url=JAVA_API_URL,
        timeout=httpx.Timeout(10.0),
        headers={
            "Authorization": f"Bearer {INTERNAL_TOKEN}",
            "Content-Type": "application/json",
        },
    ) as client:
        yield client


# ── Test identity ─────────────────────────────────────────────────

@pytest.fixture
def test_email():
    """Unique test user per run — avoids collision when re-running tests."""
    return f"integration-test-{uuid.uuid4().hex[:8]}@test.local"


# ── Shared test data ──────────────────────────────────────────────

@pytest.fixture
def valid_product():
    """Minimal valid product for creating a task."""
    return {
        "productName": "Integration Test Dress",
        "description": "A test product for integration testing",
        "imageUrls": ["https://example.com/integration-test.jpg"],
        "targetMarket": "US",
        "language": "en",
    }


@pytest.fixture
def valid_creative_plan_payload():
    """Callback payload for creative_plan stage — verified against CallbackPayload schema."""
    return {
        "taskId": str(uuid.uuid4()),
        "schemaVersion": "1.0.0",
        "stage": "creative_plan",
        "status": "success",
        "nextTaskStatus": "waiting_plan_selection",
        "plans": [
            {
                "type": "pain_point_solution",
                "title": "Test Plan",
                "hook": "This is a test hook for integration testing",
                "structure": "Hook→Product→CTA",
                "reason": "Test reason",
                "estimatedDuration": 20,
                "score": 85,
                "taskMode": "PRODUCT_CREATIVE",
                "requiredAssets": ["product_front"],
                "estimatedCostTier": "moderate",
            }
        ],
    }


@pytest.fixture
def valid_storyboard_payload():
    """Callback payload for storyboard stage."""
    task_id = str(uuid.uuid4())
    return {
        "taskId": task_id,
        "schemaVersion": "1.0.0",
        "stage": "storyboard",
        "status": "success",
        "nextTaskStatus": "waiting_storyboard_confirmation",
        "storyboard": {
            "title": "Test Storyboard",
            "hook": "Test hook",
            "duration": 20,
            "caption": "Test caption #test",
            "hashtags": ["#test"],
            "coverText": "Test Cover",
            "musicSuggestion": "Upbeat",
            "shots": [
                {
                    "shotNo": 1, "duration": 4,
                    "scene": "Opening", "action": "Model walks in",
                    "subtitle": "Check this out",
                    "materialType": "ai_video",
                    "prompt": "Fashion model walking",
                    "negativePrompt": "blurry",
                    "editInstruction": "Quick cut",
                },
                {
                    "shotNo": 2, "duration": 4,
                    "scene": "Product", "action": "Show product",
                    "subtitle": "Amazing quality",
                    "materialType": "product_image",
                    "prompt": "",
                    "negativePrompt": "",
                    "editInstruction": "Zoom in",
                },
                {
                    "shotNo": 3, "duration": 4,
                    "scene": "Detail", "action": "Close up",
                    "subtitle": "Look at the fabric",
                    "materialType": "ai_image",
                    "prompt": "Close up of fabric texture",
                    "negativePrompt": "",
                    "editInstruction": "Slow zoom",
                },
                {
                    "shotNo": 4, "duration": 4,
                    "scene": "CTA", "action": "Point to link",
                    "subtitle": "Get yours now",
                    "materialType": "ai_video",
                    "prompt": "Model smiling",
                    "negativePrompt": "",
                    "editInstruction": "Freeze at end",
                },
            ],
        },
    }


@pytest.fixture
def valid_repair_payload():
    """Callback payload for repair stage."""
    return {
        "taskId": str(uuid.uuid4()),
        "schemaVersion": "1.0.0",
        "stage": "repair",
        "status": "success",
        "nextTaskStatus": "video_clip_generating",
        "repairResult": {
            "feedbackCategory": "visual_quality",
            "targetType": "video_clip",
            "strategy": "regenerate_video_clip",
            "affectedShots": [3],
            "repairNotes": "Fabric detail blurry — regenerating with better lighting prompt",
            "preserveConstraints": {
                "productDetails": ["floral print", "a-line"],
                "styleAttributes": ["Casual", "Bohemian"],
            },
            "newPrompt": "Fashion model walking, fabric texture extremely sharp, golden hour backlight, 5s, 4K",
            "estimatedCostTier": "cheap",
            "requiresUserConfirmation": False,
        },
    }
