"""Volcengine / Doubao Seedream image generation provider.

Seedream 4.0 on Volcano Ark supports OpenAI-compatible /images/generations.
Reference images are downloaded and passed as base64 data URIs so the model
always receives actual pixel data — no dependency on server-side URL download.
"""

import base64
import logging
from typing import Optional

import httpx

from .base import ImageGenerationProvider
from src.config import settings

log = logging.getLogger(__name__)


async def _download_as_base64(url: str, timeout: float = 15.0) -> Optional[str]:
    """Download an image URL and return a data URI string.

    Returns None if the download fails for any reason.
    """
    try:
        transport = httpx.AsyncHTTPTransport(retries=0)
        async with httpx.AsyncClient(transport=transport, timeout=timeout) as client:
            resp = await client.get(url, follow_redirects=True)
            resp.raise_for_status()
        content_type = resp.headers.get("content-type", "image/png")
        if not content_type.startswith("image/"):
            content_type = "image/png"
        b64 = base64.b64encode(resp.content).decode("ascii")
        return f"data:{content_type};base64,{b64}"
    except Exception:
        log.warning("Failed to download image as base64: %s", url[:120])
        return None


class VolcengineImageProvider(ImageGenerationProvider):
    """Image generation via Volcengine Ark (doubao-seedream series).

    Downloads reference images and embeds them as base64 data URIs so the
    model always sees actual pixel data regardless of URL accessibility.
    """

    def __init__(self):
        self._model = settings.image_gen_model
        self._api_key = settings.image_gen_api_key or settings.text_llm_api_key
        self._base_url = (
            settings.image_gen_base_url
            or settings.text_llm_base_url
            or "https://ark.cn-beijing.volces.com/api/v3"
        )

    @property
    def provider_name(self) -> str:
        return "volcengine"

    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Generate a single image via Seedream on Ark.

        Reference images (source assets + previous result) are downloaded
        and embedded as base64 so the model receives pixel data directly.
        """
        try:
            from openai import AsyncOpenAI
        except ImportError:
            raise RuntimeError("openai package not installed (needed for image generation)")

        source_assets = kwargs.get("source_assets") or []
        size = kwargs.get("size", "1440x2560")
        previous_result = kwargs.get("previous_result") or {}

        # Build the full prompt
        full_prompt = prompt
        if negative_prompt:
            full_prompt = f"{prompt}. Avoid: {negative_prompt}"

        # ── Collect all reference image URLs ──
        ref_urls: list[str] = []
        seen_ref_urls: set[str] = set()
        for asset in (source_assets if isinstance(source_assets, list) else []):
            if isinstance(asset, dict) and asset.get("url"):
                url = str(asset.get("url")).strip()
                if url and url not in seen_ref_urls:
                    ref_urls.append(url)
                    seen_ref_urls.add(url)
        prev_url = previous_result.get("url") if isinstance(previous_result, dict) else None
        if isinstance(prev_url, str):
            prev_url = prev_url.strip()
            if prev_url and prev_url not in seen_ref_urls:
                ref_urls.append(prev_url)
                seen_ref_urls.add(prev_url)

        # ── Download & base64-encode every reference image ──
        log.info(
            "VolcengineImageProvider: collecting %d ref URLs: %s",
            len(ref_urls), [u[:80] + "..." for u in ref_urls],
        )
        ref_data_uris: list[str] = []
        for url in ref_urls:
            data_uri = await _download_as_base64(url)
            if data_uri:
                ref_data_uris.append(data_uri)
                log.info("VolcengineImageProvider: downloaded ref, size=%d bytes", len(data_uri))
            else:
                log.warning("VolcengineImageProvider: FAILED to download ref: %s", url[:120])

        client = AsyncOpenAI(api_key=self._api_key, base_url=self._base_url)

        is_edit = bool(kwargs.get("feedback") or kwargs.get("previous_result"))

        extra_body: dict = {}
        if ref_data_uris:
            # Seedream 4.5 multi-image format: "image" is an array of URLs
            extra_body["image"] = ref_data_uris
            extra_body["sequential_image_generation"] = "disabled"
            log.info(
                "VolcengineImageProvider: %d ref images, mode=%s",
                len(ref_data_uris), "EDIT" if is_edit else "GENERATE",
            )
        else:
            log.warning("VolcengineImageProvider: text-to-image mode — NO refs downloaded! %d URLs attempted", len(ref_urls))

        extra_body["response_format"] = "url"
        extra_body["watermark"] = False

        response = await client.images.generate(
            model=self._model,
            prompt=full_prompt,
            n=1,
            size=size,
            extra_body=extra_body if extra_body else None,
        )

        url = response.data[0].url if response.data else ""
        log.info("VolcengineImageProvider: model=%s, url=%s", self._model, url[:80] if url else "empty")

        return {
            "url": url or "",
            "provider": self.provider_name,
            "model": self._model,
            "qualityScore": None,
        }
