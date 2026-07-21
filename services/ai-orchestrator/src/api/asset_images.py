"""Synchronous task-asset image generation endpoints used by Java."""

import base64
import hashlib
import logging
import os
from pathlib import Path

import httpx
from fastapi import APIRouter, HTTPException

from src.providers import get_image_provider
from src.schemas.workflow_requests import (
    AssetImageGenerationRequest,
    AssetImageGenerationResponse,
)

log = logging.getLogger(__name__)
router = APIRouter()

UPLOAD_DIR = Path("../../apps/api-java/uploads/proxied")
UPLOAD_BASE_URL = "http://localhost:8099/uploads/proxied"


async def _translate_to_english(user_text: str) -> str:
    """Translate Chinese user instruction to English for Seedream."""
    from src.services.llm_service import call_llm

    system = (
        "Translate the user's Chinese into concise English for an image generation model. "
        "Output ONLY the English text. No markdown, no quotes, no extra commentary."
    )
    try:
        result = await call_llm("fashion_asset_analysis", system, user_text)
        translated = result.get("analysisText", "") if isinstance(result, dict) else str(result)
        if translated and len(translated) > 5:
            log.info("Translated: %s -> %s", user_text[:60], translated[:120])
            return translated.strip()
    except Exception as e:
        log.warning("Translation failed: %s", e)
    return user_text


@router.post("/assets/proxy-download")
async def proxy_download(req: dict):
    """Download an external URL and return a local URL.

    Call this before using an external image as a source asset.
    The image is saved to Java's uploads directory so it's always accessible.
    """
    url = (req.get("url") or "").strip()
    if not url:
        raise HTTPException(status_code=400, detail="url is required")

    # Already local — return as-is
    if "localhost" in url or "127.0.0.1" in url:
        return {"url": url, "proxied": False}

    transport = httpx.AsyncHTTPTransport(retries=0)
    async with httpx.AsyncClient(transport=transport, timeout=30, follow_redirects=True) as client:
        try:
            resp = await client.get(url, headers={
                "User-Agent": "Mozilla/5.0 (compatible; TK-AI-Video/1.0)",
                "Accept": "image/*",
            })
            resp.raise_for_status()
        except Exception as e:
            raise HTTPException(status_code=502, detail=f"Download failed: {e}")

    content_type = resp.headers.get("content-type", "image/png")
    ext = ".png"
    if "jpeg" in content_type or "jpg" in content_type:
        ext = ".jpg"
    elif "webp" in content_type:
        ext = ".webp"

    # Save to Java's uploads directory
    url_hash = hashlib.md5(url.encode()).hexdigest()[:12]
    filename = f"{url_hash}{ext}"
    target_dir = UPLOAD_DIR
    target_dir.mkdir(parents=True, exist_ok=True)
    target_path = target_dir / filename

    with open(target_path, "wb") as f:
        f.write(resp.content)

    local_url = f"{UPLOAD_BASE_URL}/{filename}"
    log.info("Proxied: %s -> %s (%d bytes)", url[:80], local_url, len(resp.content))
    return {"url": local_url, "proxied": True, "size": len(resp.content)}


@router.post("/assets/generate-image", response_model=AssetImageGenerationResponse)
async def generate_asset_image(req: AssetImageGenerationRequest):
    """Generate one task-level image asset and return its URL to Java."""

    provider = get_image_provider()

    # Translate Chinese instruction to English
    instruction = (req.feedback or req.prompt or "").strip()
    has_chinese = any('一' <= c <= '鿿' for c in instruction)
    if has_chinese:
        translated = await _translate_to_english(instruction)
        if req.feedback:
            req.feedback = translated
        else:
            req.prompt = translated

    # Minimal prompt — the images carry the visual context
    prompt = (req.feedback or req.prompt or "").strip()
    negative_prompt = "watermark, text, logo, low quality, blurry, distorted"

    try:
        result = await provider.generate(
            prompt=prompt,
            negative_prompt=negative_prompt,
            purpose=req.assetRole or "image_variant",
            source_assets=req.sourceAssets,
            size="1440x2560",
            previous_result=req.previousResult,
            feedback=req.feedback,
            previous_prompt=req.previousPrompt,
        )
    except Exception as exc:
        log.exception("Asset image generation failed: taskId=%s", req.taskId)
        raise HTTPException(status_code=502, detail=str(exc)) from exc

    url = result.get("url") or ""
    if not url:
        raise HTTPException(status_code=502, detail="Image provider returned empty URL")

    return AssetImageGenerationResponse(
        url=url,
        provider=result.get("provider", provider.provider_name),
        model=result.get("model", "unknown"),
        prompt=prompt,
        negativePrompt=negative_prompt,
        qualityScore=result.get("qualityScore"),
    )
