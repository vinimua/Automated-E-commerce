"""Provider factory — selects the active image generation provider."""

import logging
from .base import ImageGenerationProvider
from .fake_image import FakeImageProvider
from .openai_image import OpenAIImageProvider
from src.config import settings

log = logging.getLogger(__name__)


def get_image_provider() -> ImageGenerationProvider:
    """Return the active ImageGenerationProvider based on feature flags.

    - ENABLE_IMAGE_GENERATION=false → FakeImageProvider (dev, zero cost)
    - ENABLE_IMAGE_GENERATION=true + image_gen_provider=openai → OpenAIImageProvider
    """
    if not settings.enable_image_generation:
        log.info("Image generation disabled — using FakeImageProvider")
        return FakeImageProvider()

    provider_name = settings.image_gen_provider.lower()
    if provider_name in ("openai", "openai_compatible"):
        log.info("Using OpenAIImageProvider (model=%s)", settings.image_gen_model)
        return OpenAIImageProvider()

    log.warning("Unknown image provider '%s' — falling back to FakeImageProvider", provider_name)
    return FakeImageProvider()
