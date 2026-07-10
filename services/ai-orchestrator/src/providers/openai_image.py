"""OpenAI / OpenAI-compatible image generation provider (DALL-E)."""

import logging
from .base import ImageGenerationProvider
from src.config import settings

log = logging.getLogger(__name__)


class OpenAIImageProvider(ImageGenerationProvider):
    """Image generation via OpenAI DALL-E or compatible API.

    Gated by ENABLE_IMAGE_GENERATION=true.
    Uses IMAGE_GEN_API_KEY and IMAGE_GEN_BASE_URL from config.
    """

    def __init__(self):
        self._model = settings.image_gen_model

    @property
    def provider_name(self) -> str:
        return settings.image_gen_provider

    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Generate a single image via DALL-E or compatible API."""
        try:
            from openai import AsyncOpenAI
        except ImportError:
            raise RuntimeError("openai package not installed (needed for image generation)")

        api_key = settings.image_gen_api_key or settings.text_llm_api_key
        base_url = settings.image_gen_base_url or settings.text_llm_base_url or None

        # Build the full prompt incorporating negative prompt
        full_prompt = prompt
        if negative_prompt:
            full_prompt = f"{prompt}. Avoid: {negative_prompt}"

        size = kwargs.get("size", "1024x1024")
        quality = kwargs.get("quality", "standard")

        client = AsyncOpenAI(api_key=api_key, base_url=base_url)
        response = await client.images.generate(
            model=self._model,
            prompt=full_prompt,
            n=1,
            size=size,
            quality=quality,
        )

        url = response.data[0].url if response.data else ""
        log.info("OpenAIImageProvider: model=%s, size=%s, url=%s", self._model, size, url)

        return {
            "url": url or "",
            "provider": self.provider_name,
            "model": self._model,
            "qualityScore": None,  # No built-in quality score from DALL-E
        }
