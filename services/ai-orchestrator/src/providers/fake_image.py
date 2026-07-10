"""Fake image provider — returns placeholder URLs for development."""

import logging
from .base import ImageGenerationProvider

log = logging.getLogger(__name__)

PLACEHOLDER_BASE = "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes"


class FakeImageProvider(ImageGenerationProvider):
    """Returns placeholder image URLs. Zero cost, no API call."""

    def __init__(self):
        self._counter = 0

    @property
    def provider_name(self) -> str:
        return "fake"

    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Return a placeholder image URL. Increments counter for unique URLs."""
        self._counter += 1
        shot_no = kwargs.get("shot_no", self._counter)
        purpose = kwargs.get("purpose", "first_frame")

        url = f"{PLACEHOLDER_BASE}/shot{shot_no}_{purpose}.png"
        log.info("FakeImageProvider: shot=%d, purpose=%s, url=%s", shot_no, purpose, url)

        return {
            "url": url,
            "provider": self.provider_name,
            "model": "fake-v1",
            "qualityScore": 85 + (shot_no % 5),  # 85-89 range
        }
