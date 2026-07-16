"""Shared pytest fixtures for AI orchestrator tests."""

import pytest

from src.config import settings


@pytest.fixture(autouse=True)
def force_fake_llm_for_tests(monkeypatch):
    """Unit tests must never call paid or external model APIs from local .env."""
    monkeypatch.setattr(settings, "force_fake_llm", True)


@pytest.fixture
def sample_product_context():
    """Minimal product context matching Java's expected format."""
    return {
        "name": "Summer Floral Dress",
        "description": "Lightweight A-line floral print dress, knee-length, cotton-linen blend",
        "images": ["https://example.com/dress_front.jpg", "https://example.com/dress_back.jpg"],
        "targetMarket": "US",
        "language": "en",
    }


@pytest.fixture
def sample_storyboard():
    """Minimal 3-shot storyboard for testing."""
    return {
        "title": "Test Storyboard",
        "hook": "Test hook",
        "duration": 15,
        "caption": "Test caption",
        "hashtags": ["#test"],
        "coverText": "Test Cover",
        "musicSuggestion": "Upbeat",
        "shots": [
            {
                "shotNo": 1, "duration": 3,
                "scene": "Opening shot",
                "action": "Model enters",
                "subtitle": "Look at this dress",
                "materialType": "ai_image",
                "prompt": "Fashion model entering frame, sunny day",
                "negativePrompt": "blurry",
                "editInstruction": "Quick cut",
            },
            {
                "shotNo": 2, "duration": 4,
                "scene": "Product detail",
                "action": "Close-up of fabric",
                "subtitle": "Premium cotton blend",
                "materialType": "product_image",
                "prompt": "",
                "negativePrompt": "",
                "editInstruction": "Slow zoom",
            },
            {
                "shotNo": 3, "duration": 3,
                "scene": "CTA",
                "action": "Model points to link",
                "subtitle": "Shop now!",
                "materialType": "ai_video",
                "prompt": "Model smiling, pointing down",
                "negativePrompt": "blurry face",
                "editInstruction": "Freeze frame",
            },
        ],
    }


@pytest.fixture
def sample_plan():
    """A single selected creative plan."""
    return {
        "type": "pain_point_solution",
        "title": "Test Plan",
        "hook": "Test hook for plan",
        "structure": "Pain→Product→Solution→CTA",
        "reason": "Best emotional resonance",
        "estimatedDuration": 20,
        "score": 88,
        "taskMode": "PRODUCT_CREATIVE",
        "requiredAssets": ["product_front", "product_detail"],
        "estimatedCostTier": "moderate",
    }


@pytest.fixture
def sample_keyframes():
    """Minimal keyframe result for testing."""
    return {
        "keyframes": [
            {"shotNo": 1, "status": "completed", "url": "https://example.com/kf1.png", "provider": "fake", "modelName": "fake-v1", "qualityScore": 85, "source": "ai_generated", "imagePurpose": "first_frame"},
            {"shotNo": 2, "status": "completed", "url": "https://example.com/kf2.png", "provider": "fake", "modelName": "fake-v1", "qualityScore": 90, "source": "ai_generated", "imagePurpose": "product_detail"},
            {"shotNo": 3, "status": "completed", "url": "https://example.com/kf3.png", "provider": "fake", "modelName": "fake-v1", "qualityScore": 88, "source": "ai_generated", "imagePurpose": "first_frame"},
        ]
    }


@pytest.fixture
def task_id():
    return "550e8400-e29b-41d4-a716-446655440000"
