"""Synchronous task-asset image generation endpoints used by Java."""

import logging

from fastapi import APIRouter, HTTPException

from src.providers import get_image_provider
from src.schemas.workflow_requests import (
    AssetImageGenerationRequest,
    AssetImageGenerationResponse,
)

log = logging.getLogger(__name__)
router = APIRouter()


def _format_source_assets(req: AssetImageGenerationRequest) -> str:
    source_lines: list[str] = []
    for asset in req.sourceAssets:
        if not isinstance(asset, dict):
            continue
        asset_id = asset.get("assetId") or asset.get("id")
        role = asset.get("assetRole") or asset.get("role")
        source = asset.get("source") or ""
        url = asset.get("url")
        if url:
            source_lines.append(f"- assetId={asset_id}, role={role}, source={source}, url={url}")
    return "\n".join(source_lines) if source_lines else "- 未提供可用参考素材 URL"


def _product_context_lines(req: AssetImageGenerationRequest) -> str:
    product_profile = req.productContext.get("productProfile") or req.productContext.get("product") or {}
    product_name = product_profile.get("name") or req.productContext.get("name") or ""
    product_description = product_profile.get("description") or req.productContext.get("description") or ""
    return f"- name: {product_name}\n- description: {product_description}"


def _build_asset_image_prompt(req: AssetImageGenerationRequest) -> str:
    """Build a provider prompt for a generated/edited product image.

    For regeneration, the previous generated image is the visual source of truth.
    previousPrompt is included only as low-priority intent history so shorthand
    feedback such as "make it smaller" has an antecedent.
    """
    is_revision = bool(req.feedback or req.previousResult)
    if is_revision:
        instruction = (req.feedback or req.prompt).strip()
        previous_intent = (req.previousPrompt or "").strip() or "无。只根据当前图片和本轮修改要求执行。"
        return f"""
你正在对一张已有图片做局部编辑。
传给你的参考图就是需要编辑的底图，不是风格参考，也不是重新生成参考。

当前底图是最高优先级事实。历史编辑意图只能帮助理解省略表达，不能覆盖底图像素。

历史编辑意图（低优先级，仅用于理解“再小一点”“往左一点”等省略反馈）：
{previous_intent}

本轮用户明确要求（最高优先级操作指令）：
{instruction}

参考底图：
{_format_source_assets(req)}

编辑规则：
1. 如果本轮要求是“再小一点、再大一点、往左一点、浅一点”等省略表达，请结合历史编辑意图判断它指的是哪个已有元素。
2. 如果历史编辑意图与底图实际画面冲突，必须相信底图，只编辑底图中真实存在的元素。
3. 不要重新生成整张图，只做局部修改。
4. 除用户明确要求修改的区域外，商品主体、版型、颜色、材质、背景、光线、构图都保持一致。
5. 如果用户要求调整已有元素，必须保留同一个元素的外观特征，只改变用户指定的属性。
6. 不要移动、删除或新增用户没有提到的元素。
7. 输出结果应该看起来就是参考图被轻微编辑后的版本，而不是一张新图。
8. 不要添加水印、乱码文字、虚构品牌标识、夸张功能效果或参考图中不存在的新结构。
""".strip()

    return f"""
你是电商服装商品图生成/编辑模型。

你的任务：
根据参考素材、商品背景和用户要求，生成一张可作为“当前商品新确认图”的候选图。该图后续会进入素材分析、创意方案和分镜生成，所以必须清晰、稳定、可验证。

用户当前要求：
{req.prompt}

商品背景：
{_product_context_lines(req)}

参考素材：
{_format_source_assets(req)}

生成规则：
1. 保持参考素材中可见的商品主体一致，除非用户明确要求修改某个元素。
2. 如果用户要求添加、替换或调整图案，只修改指定区域；不要随意改变版型、主色、领型、袖型、衣长、比例和材质视觉。
3. 不要添加水印、乱码文字、虚构品牌标识、夸张功能效果或素材里不存在的结构。
4. 输出适合电商短视频前置素材分析的清晰商品图：主体明确，细节可见，背景不抢主体。
5. 竖版 9:16 构图优先，商品在画面中占比充足。
""".strip()


@router.post("/assets/generate-image", response_model=AssetImageGenerationResponse)
async def generate_asset_image(req: AssetImageGenerationRequest):
    """Generate one task-level image asset and return its URL to Java.

    Java remains responsible for ownership, persistence and task status.
    """
    provider = get_image_provider()
    prompt = _build_asset_image_prompt(req)
    negative_prompt = "watermark, logo hallucination, unreadable text, extra limbs, distorted garment, low quality, blurry"

    try:
        result = await provider.generate(
            prompt=prompt,
            negative_prompt=negative_prompt,
            purpose=req.assetRole or "image_variant",
            source_assets=req.sourceAssets,
            size="1024x1792",
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
