"""Fake image provider — returns deterministic inline preview images for development."""

import base64
import logging

from .base import ImageGenerationProvider

log = logging.getLogger(__name__)


class FakeImageProvider(ImageGenerationProvider):
    """Returns deterministic SVG data URLs. Zero cost, no API call."""

    def __init__(self):
        self._counter = 0

    @property
    def provider_name(self) -> str:
        return "fake"

    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Return an inline SVG preview. Increments counter for unique fallback shot numbers."""
        self._counter += 1
        shot_no = int(kwargs.get("shot_no", self._counter) or self._counter)
        purpose = str(kwargs.get("purpose", "first_frame") or "first_frame")

        url = _build_fake_keyframe_data_url(shot_no, purpose)
        log.info("FakeImageProvider: shot=%d, purpose=%s, url=data:image/svg+xml;base64,...", shot_no, purpose)

        return {
            "url": url,
            "provider": self.provider_name,
            "model": "fake-v1",
            "qualityScore": 85 + (shot_no % 5),  # 85-89 range
        }


def _build_fake_keyframe_data_url(shot_no: int, purpose: str) -> str:
    label = f"Shot {shot_no}"
    subtitle = purpose.replace("_", " ")
    hue = (shot_no * 47) % 360
    accent_hue = (hue + 70) % 360
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="720" height="1280" viewBox="0 0 720 1280">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="hsl({hue}, 82%, 62%)"/>
      <stop offset="100%" stop-color="hsl({accent_hue}, 80%, 38%)"/>
    </linearGradient>
  </defs>
  <rect width="720" height="1280" fill="url(#bg)"/>
  <rect x="72" y="188" width="576" height="904" rx="48" fill="rgba(255,255,255,0.18)" stroke="rgba(255,255,255,0.42)" stroke-width="3"/>
  <circle cx="360" cy="484" r="138" fill="rgba(255,255,255,0.28)"/>
  <path d="M252 780 C310 700 410 700 468 780 L540 912 H180 Z" fill="rgba(255,255,255,0.32)"/>
  <text x="360" y="612" text-anchor="middle" font-family="Arial, sans-serif" font-size="64" font-weight="700" fill="white">{label}</text>
  <text x="360" y="690" text-anchor="middle" font-family="Arial, sans-serif" font-size="34" fill="rgba(255,255,255,0.88)">{subtitle}</text>
  <text x="360" y="1030" text-anchor="middle" font-family="Arial, sans-serif" font-size="28" fill="rgba(255,255,255,0.78)">Fake keyframe preview</text>
</svg>"""
    encoded = base64.b64encode(svg.encode("utf-8")).decode("ascii")
    return f"data:image/svg+xml;base64,{encoded}"
