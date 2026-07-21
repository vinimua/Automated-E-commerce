"""Volcengine / Doubao Seedance video generation provider.

Seedance on Volcano Ark uses an async task API:
  POST /api/v3/contents/generations/tasks     → submit
  GET  /api/v3/contents/generations/tasks/{id} → poll until done

Supported models: doubao-seedance-1-5-pro-251215, etc.
"""

import asyncio
import logging
from typing import Optional

import httpx

from .base import VideoGenerationProvider
from src.config import settings

log = logging.getLogger(__name__)

POLL_INTERVAL = 5  # seconds
MAX_POLL_TIME = 300  # seconds (5 minutes max wait)


class VolcengineVideoProvider(VideoGenerationProvider):
    """Video generation via Volcengine Ark (doubao-seedance series)."""

    def __init__(self):
        self._model = settings.video_gen_model
        self._api_key = settings.video_gen_api_key or settings.image_gen_api_key or settings.text_llm_api_key
        self._base_url = (
            settings.video_gen_base_url
            or settings.image_gen_base_url
            or settings.text_llm_base_url
            or "https://ark.cn-beijing.volces.com/api/v3"
        )

    @property
    def provider_name(self) -> str:
        return "volcengine"

    async def generate(self, prompt: str, negative_prompt: str = "", **kwargs) -> dict:
        """Generate a video clip via Seedance (image-to-video).

        Args:
            prompt: Text prompt describing the video.
            **kwargs: May include:
                - source_images: list[dict] of reference image URLs (first frame reference).
                - duration: desired duration in seconds (default 5).

        Returns:
            dict with keys: url, provider, model, task_id.
        """
        duration = int(kwargs.get("duration") or 5)
        source_images = kwargs.get("source_images") or []

        # Build prompt with official parameter suffixes
        full_prompt = self._build_prompt(prompt, duration)

        # Build content array (text + optional reference image)
        content: list[dict] = [{"type": "text", "text": full_prompt}]
        if source_images:
            ref_url = source_images[0].get("url") if isinstance(source_images[0], dict) else str(source_images[0])
            if ref_url:
                data_uri = await _download_as_base64(ref_url)
                if data_uri:
                    content.append({"type": "image_url", "image_url": {"url": data_uri}})

        # Submit task
        task_id = await self._submit_task(content)
        log.info("VolcengineVideoProvider: task submitted, id=%s, model=%s", task_id, self._model)

        # Poll until done
        video_url = await self._poll_task(task_id)
        log.info("VolcengineVideoProvider: task completed, url=%s", video_url[:80] if video_url else "empty")

        return {
            "url": video_url or "",
            "provider": self.provider_name,
            "model": self._model,
            "task_id": task_id,
        }

    def _build_prompt(self, prompt: str, duration: int) -> str:
        """Build prompt with official Seedance parameter suffixes."""
        return f"{prompt} --duration {max(2, min(duration, 12))} --camerafixed false --watermark false"

    async def _submit_task(self, content: list[dict]) -> str:
        """Submit a video generation task, return task_id."""
        transport = httpx.AsyncHTTPTransport(retries=0)
        async with httpx.AsyncClient(transport=transport, timeout=60.0) as client:
            resp = await client.post(
                f"{self._base_url}/contents/generations/tasks",
                json={"model": self._model, "content": content},
                headers={
                    "Authorization": f"Bearer {self._api_key}",
                    "Content-Type": "application/json",
                },
            )
            if not resp.is_success:
                raise RuntimeError(f"Submit failed: HTTP {resp.status_code} - {resp.text[:500]}")
            data = resp.json()
            task_id = data.get("id") or data.get("task_id")
            if not task_id:
                raise RuntimeError(f"No task_id in response: {data}")
            return task_id

    async def _poll_task(self, task_id: str) -> Optional[str]:
        """Poll task status until completion. Returns video URL or raises."""
        transport = httpx.AsyncHTTPTransport(retries=0)
        elapsed = 0
        async with httpx.AsyncClient(transport=transport, timeout=30.0) as client:
            while elapsed < MAX_POLL_TIME:
                await asyncio.sleep(POLL_INTERVAL)
                elapsed += POLL_INTERVAL
                resp = await client.get(
                    f"{self._base_url}/contents/generations/tasks/{task_id}",
                    headers={"Authorization": f"Bearer {self._api_key}"},
                )
                if not resp.is_success:
                    log.warning("Poll %s failed: HTTP %d", task_id, resp.status_code)
                    continue
                data = resp.json()
                status = data.get("status", "")
                if status == "succeeded":
                    video_url = data.get("content", {}).get("video_url") or data.get("video_url")
                    if not video_url:
                        raise RuntimeError(f"Task {task_id} succeeded but no video_url in response: {str(data)[:500]}")
                    return video_url
                if status == "failed":
                    error = data.get("error", {}).get("message") or str(data.get("error", ""))
                    raise RuntimeError(f"Task {task_id} failed: {error}")
                log.info("Poll %s: status=%s, elapsed=%ds", task_id, status, elapsed)

        raise TimeoutError(f"Task {task_id} did not complete within {MAX_POLL_TIME}s")


async def _download_as_base64(url: str, timeout: float = 15.0) -> Optional[str]:
    """Download an image URL and return a data URI string."""
    try:
        transport = httpx.AsyncHTTPTransport(retries=0)
        async with httpx.AsyncClient(transport=transport, timeout=timeout) as client:
            resp = await client.get(url, follow_redirects=True)
            resp.raise_for_status()
        import base64
        content_type = resp.headers.get("content-type", "image/png")
        if not content_type.startswith("image/"):
            content_type = "image/png"
        b64 = base64.b64encode(resp.content).decode("ascii")
        return f"data:{content_type};base64,{b64}"
    except Exception:
        log.warning("Failed to download image as base64: %s", url[:120])
        return None
