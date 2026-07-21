"""Provider factory — selects active image / video generation providers."""

import logging
from .base import ImageGenerationProvider, VideoGenerationProvider
from .fake_image import FakeImageProvider
from .openai_image import OpenAIImageProvider
from .volcengine_image import VolcengineImageProvider
from .volcengine_video import VolcengineVideoProvider
from src.config import settings

log = logging.getLogger(__name__)


def get_image_provider() -> ImageGenerationProvider:
    """Return the active ImageGenerationProvider based on feature flags."""
    if not settings.enable_image_generation:
        log.info("Image generation disabled — using FakeImageProvider")
        return FakeImageProvider()

    provider_name = settings.image_gen_provider.lower()
    if provider_name in ("openai", "openai_compatible"):
        log.info("Using OpenAIImageProvider (model=%s)", settings.image_gen_model)
        return OpenAIImageProvider()
    if provider_name == "volcengine":
        log.info("Using VolcengineImageProvider (model=%s)", settings.image_gen_model)
        return VolcengineImageProvider()

    log.warning("Unknown image provider '%s' — falling back to FakeImageProvider", provider_name)
    return FakeImageProvider()


class FakeVideoProvider(VideoGenerationProvider):
    """Returns placeholder video URLs. Zero cost, no API call."""

    @property
    def provider_name(self) -> str:
        return "fake"

    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        url = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
        shot_no = kwargs.get("shot_no", 0)
        return {
            "url": url,
            "provider": self.provider_name,
            "model": "fake-video-v1",
            "duration": int(kwargs.get("duration") or 3),
        }


def get_video_provider() -> VideoGenerationProvider:
    """Return the active VideoGenerationProvider based on feature flags."""
    if not settings.enable_video_generation:
        log.info("Video generation disabled — using FakeVideoProvider")
        return FakeVideoProvider()

    provider_name = settings.video_gen_provider.lower()
    if provider_name == "volcengine":
        log.info("Using VolcengineVideoProvider (model=%s)", settings.video_gen_model)
        return VolcengineVideoProvider()

    log.warning("Unknown video provider '%s' — falling back to FakeVideoProvider", provider_name)
    return FakeVideoProvider()
