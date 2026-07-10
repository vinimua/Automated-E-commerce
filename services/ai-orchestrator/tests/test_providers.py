"""Test ImageGenerationProvider implementations."""

import pytest
from src.providers.base import ImageGenerationProvider
from src.providers.fake_image import FakeImageProvider
from src.providers.openai_image import OpenAIImageProvider
from src.providers.factory import get_image_provider


class TestFakeImageProvider:
    def test_is_image_provider(self):
        provider = FakeImageProvider()
        assert isinstance(provider, ImageGenerationProvider)

    def test_provider_name(self):
        provider = FakeImageProvider()
        assert provider.provider_name == "fake"

    @pytest.mark.asyncio
    async def test_generate_returns_url(self):
        provider = FakeImageProvider()
        result = await provider.generate(
            prompt="Fashion keyframe: model in dress",
            negative_prompt="blurry, low quality",
            shot_no=3,
            purpose="first_frame",
        )
        assert "url" in result
        assert result["url"].startswith("https://placeholder.cos.")
        assert "shot3" in result["url"] or "shot_3" in result["url"]
        assert result["provider"] == "fake"
        assert result["model"] == "fake-v1"
        assert "qualityScore" in result

    @pytest.mark.asyncio
    async def test_generate_increments_counter(self):
        provider = FakeImageProvider()
        r1 = await provider.generate(prompt="test 1")
        r2 = await provider.generate(prompt="test 2")
        # URLs should differ (different shot numbers / counters)
        assert r1["url"] != r2["url"]


class TestOpenAIProvider:
    def test_is_image_provider(self):
        provider = OpenAIImageProvider()
        assert isinstance(provider, ImageGenerationProvider)

    def test_provider_name_from_config(self):
        from src.config import settings
        provider = OpenAIImageProvider()
        assert provider.provider_name == settings.image_gen_provider


class TestProviderFactory:
    def test_returns_fake_when_disabled(self):
        """With ENABLE_IMAGE_GENERATION=false (default), factory returns FakeImageProvider."""
        provider = get_image_provider()
        # In test environment, ENABLE_IMAGE_GENERATION is false by default
        assert isinstance(provider, ImageGenerationProvider)
        # Factory should return FakeImageProvider when disabled
        assert provider.provider_name == "fake"

    def test_returns_consistent_type(self):
        """Multiple calls return fresh instances."""
        p1 = get_image_provider()
        p2 = get_image_provider()
        assert isinstance(p1, type(p2))
