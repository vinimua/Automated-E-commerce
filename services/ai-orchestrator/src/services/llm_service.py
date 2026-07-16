"""LLM service — OpenAI/Anthropic wrapper with fake provider for dev.
这个文件是 AI 调用的统一入口，做一件事：根据配置决定用假数据还是真 AI。"""

import asyncio
import base64
import json
import os
import logging
from urllib.parse import urlparse

import httpx

from src.config import settings

log = logging.getLogger(__name__)

# Fixture data for fake mode (when no API key is configured)
FIXTURES = {
    "product_analysis": {
        "category": "Electronics",
        "sellingPoints": ["Noise cancelling", "Long battery life", "Bluetooth 5.3"],
        "painPoints": ["Poor fit in ears", "Battery dies quickly", "Connection drops"],
        "targetAudience": ["Commuters", "Fitness enthusiasts", "Remote workers"],
        "scenes": ["Office", "Gym", "Subway", "Coffee shop"],
        "recommendedVideoTypes": ["pain_point_solution", "before_after", "review"],
        "videoScore": 78,
        "riskTips": ["Avoid claiming medical-grade noise reduction"],
        "claimRiskLevel": "low",
        "forbiddenClaims": [],
        "complianceTips": ["Note battery life under typical conditions"],
        "needsHumanReview": False,
    },
    "video_plans": {
        "plans": [
            {
                "type": "pain_point_solution",
                "title": "Say Goodbye to Noise",
                "hook": "Your commute just got quieter",
                "structure": "Pain point → Product → Solution → Result → CTA",
                "reason": "Directly addresses the biggest commuter pain point",
                "estimatedDuration": 22,
                "score": 88,
            },
            {
                "type": "before_after",
                "title": "Noise Cancelling: Before & After",
                "hook": "Hear the difference noise cancelling makes",
                "structure": "Before → Product in use → After → Compare → CTA",
                "reason": "Visual contrast of noisy vs quiet environment",
                "estimatedDuration": 20,
                "score": 82,
            },
            {
                "type": "review",
                "title": "Honest Review: Wireless Earbuds",
                "hook": "I tested these for a week — here's the truth",
                "structure": "Question → Product intro → Test → Results → Recommendation",
                "reason": "Review format builds trust with skeptical buyers",
                "estimatedDuration": 25,
                "score": 85,
            },
        ]
    },
    "script": {
        "title": "Say Goodbye to Noise",
        "hook": "Your commute just got quieter",
        "script": "Tired of train noise drowning out your music? These earbuds change everything...",
        "caption": "The best noise-cancelling earbuds for under $100. #commute #earbuds #tiktokshop",
        "hashtags": ["#commute", "#earbuds", "#tiktokshop", "#noisecancelling"],
    },
    "storyboard": {
        "title": "Say Goodbye to Noise",
        "hook": "Your commute just got quieter",
        "duration": 22,
        "caption": "The best noise-cancelling earbuds for under $100",
        "hashtags": ["#commute", "#earbuds", "#tiktokshop"],
        "coverText": "Noise Cancelling Under $100",
        "musicSuggestion": "Upbeat tech review background music",
        "shots": [
            {
                "shotNo": 1, "duration": 3,
                "scene": "Commuter on noisy train, frustrated by sound",
                "action": "Actor removes old earbuds with frustrated expression",
                "subtitle": "Your commute just got quieter",
                "materialType": "ai_video",
                "prompt": "Person on a busy train looking frustrated, pulling out earbuds",
                "negativePrompt": "blurry, low quality, watermark",
                "editInstruction": "Quick cut, close-up on facial expression",
            },
            {
                "shotNo": 2, "duration": 4,
                "scene": "Product reveal — earbuds case opens",
                "action": "Smooth reveal of product from case",
                "subtitle": "Meet the earbuds that change everything",
                "materialType": "product_image",
                "prompt": "",
                "negativePrompt": "",
                "editInstruction": "Zoom in on product, premium lighting",
            },
            {
                "shotNo": 3, "duration": 7,
                "scene": "User puts earbuds in, smile appears",
                "action": "User places earbuds, instant relief on face",
                "subtitle": "Active noise cancelling at your fingertips",
                "materialType": "ai_video",
                "prompt": "Person putting wireless earbuds in, expression changes from stressed to relaxed",
                "negativePrompt": "deformed hands, blurry",
                "editInstruction": "Slow zoom, focus on facial transformation",
            },
            {
                "shotNo": 4, "duration": 5,
                "scene": "Product close-up with specs overlay",
                "action": "Rotate product, show key features as text overlays",
                "subtitle": "30hr battery · Bluetooth 5.3 · IPX5 waterproof",
                "materialType": "product_image_motion",
                "prompt": "",
                "negativePrompt": "",
                "editInstruction": "Product rotation with text overlay animation",
            },
            {
                "shotNo": 5, "duration": 3,
                "scene": "Call to action — shop now",
                "action": "Text animation with product and shop button",
                "subtitle": "Grab yours at the link below!",
                "materialType": "text_animation",
                "prompt": "",
                "negativePrompt": "",
                "editInstruction": "Bold text, flashing CTA button overlay",
            },
        ],
    },
    "materials": {
        "materials": [
            {"shotNo": 1, "type": "video", "status": "completed", "provider": "openai", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/shot1.mp4"},
            {"shotNo": 2, "type": "product_image", "status": "completed", "provider": "cos", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/shot2.jpg"},
            {"shotNo": 3, "type": "video", "status": "completed", "provider": "openai", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/shot3.mp4"},
            {"shotNo": 4, "type": "product_image", "status": "completed", "provider": "cos", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/shot4.jpg"},
        ]
    },
    "quality_check": {
        "qualityScore": 86,
        "riskScore": 12,
        "checks": {
            "hasHook": True, "productAppearsEarly": True,
            "subtitleReadable": True, "noSensitiveClaims": True,
            "visualQualityAcceptable": True,
        },
        "complianceTips": ["Avoid guaranteed or medical-style claims"],
        "forbiddenClaims": [],
        "needsHumanReview": False,
        "suggestions": ["The hook is clear.", "Consider making the product appear in the first 3 seconds."],
    },
}


# ── Fashion Creative Loop V1 fixtures (fake mode) ──

FASHION_FIXTURES = {
    "fashion_asset_analysis": {
        "schemaVersion": "1.0",
        "analysisText": "The assets show a blue and white floral bohemian dress with a flowing skirt. The strongest creative opportunity is movement-led vacation storytelling using walking and gentle turns. Avoid generic product slideshows, changing the floral pattern, or inventing unseen back details because the available reference only supports the front view.",
        "analyzedAssetIds": ["fixture-asset-1"],
        "model": "fixture-vision-model",
        "analyzedAt": "2026-01-01T00:00:00Z",
    },
    "reference_video_analysis": {
        "title": "Summer Collection BTS",
        "duration": 28.5,
        "hook": "What I wear in a day: summer edition",
        "structure": ["hook", "transition"],  # kept minimal for fixture
        "shots": [
            {
                "shotNo": 1, "startTime": 0.0, "endTime": 3.2, "duration": 3.2,
                "scene": "Model walks into frame, sunny outdoor setting",
                "action": "Casual walk toward camera, hair flip",
                "camera": "Medium shot, eye level",
                "transition": "Quick cut",
                "subtitle": "Summer day outfit #1",
                "structureRole": "hook",
            },
            {
                "shotNo": 2, "startTime": 3.2, "endTime": 6.8, "duration": 3.6,
                "scene": "Full outfit reveal with slow spin",
                "action": "Slow 360 spin showing dress flow",
                "camera": "Full body, slight low angle",
                "transition": "Fade",
                "subtitle": "Obsessed with this floral print",
                "structureRole": "body",
            },
            {
                "shotNo": 3, "startTime": 6.8, "endTime": 10.0, "duration": 3.2,
                "scene": "Close-up of fabric texture and print detail",
                "action": "Hand runs over fabric, macro shot",
                "camera": "Macro close-up",
                "transition": "Quick cut",
                "subtitle": "Look at the details!",
                "structureRole": "body",
            },
        ],
        "reusablePatterns": [
            "Hook → outfit reveal → detail close-up",
            "Natural outdoor lighting aesthetic",
            "POV-style text overlays",
        ],
        "riskTips": ["Avoid showing identifiable brand logos in background"],
    },
    "fashion_plans": {
        "plans": [
            {
                "type": "pain_point_solution",
                "title": "从没衣服穿到天天被问链接",
                "hook": "衣柜塞满了但早上还是不知道穿什么？这条裙子是答案",
                "structure": "痛点→产品→解决→效果→CTA",
                "reason": "直接命中女性选衣难的痛点，情感共鸣最强",
                "estimatedDuration": 22,
                "score": 90,
                "taskMode": "PRODUCT_CREATIVE",
                "requiredAssets": ["product_front", "product_back", "product_detail", "scene_reference"],
                "estimatedCostTier": "moderate",
            },
            {
                "type": "before_after",
                "title": "普通连衣裙 VS 这条法式茶歇裙",
                "hook": "同样的身高体重，穿对裙子差这么多？",
                "structure": "结果预告→对比→产品特写→CTA",
                "reason": "视觉对比强烈，适合身材修饰类卖点",
                "estimatedDuration": 20,
                "score": 85,
                "taskMode": "PRODUCT_CREATIVE",
                "requiredAssets": ["product_front", "outfit_reference", "scene_reference"],
                "estimatedCostTier": "cheap",
            },
            {
                "type": "review",
                "title": "买了12条夏裙，这条我穿了整整一周",
                "hook": "一周实测：这条百元连衣裙到底值不值？",
                "structure": "问题→产品→测试→结果→推荐",
                "reason": "实测内容在TikTok转化率极高，信任感强",
                "estimatedDuration": 25,
                "score": 87,
                "taskMode": "PRODUCT_CREATIVE",
                "requiredAssets": ["product_front", "product_detail", "model_reference"],
                "estimatedCostTier": "moderate",
            },
        ]
    },
    "fashion_storyboard": {
        "title": "从没衣服穿到天天被问链接",
        "hook": "衣柜塞满了但早上还是不知道穿什么？这条裙子是答案",
        "duration": 22,
        "caption": "被问了800遍的连衣裙链接在这里！显瘦法式茶歇裙，通勤约会都能穿 #连衣裙 #夏季穿搭",
        "hashtags": ["#连衣裙", "#夏季穿搭", "#显瘦穿搭", "#法式风格", "#tiktokshop"],
        "coverText": "被问爆的连衣裙",
        "musicSuggestion": "轻快法式风背景音乐，BPM 110-120",
        "shots": [
            {
                "shotNo": 1, "duration": 3,
                "scene": "女生站在衣柜前，一堆衣服散落，表情困扰",
                "action": "从衣柜里抽出几件衣服又丢回去，摇头叹气",
                "subtitle": "衣柜满了，但没衣服穿…",
                "materialType": "ai_video",
                "prompt": "Young woman standing in front of open closet with clothes scattered, frustrated expression, cinematic lighting, realistic",
                "negativePrompt": "blurry, low quality, deformed face, watermark, text",
                "editInstruction": "Quick cuts, handheld camera feel, zoom on frustrated face",
            },
            {
                "shotNo": 2, "duration": 2,
                "scene": "产品惊艳亮相——裙子挂在简约衣架上",
                "action": "裙子从模糊变清晰，光线打在裙子上",
                "subtitle": "直到我发现了它",
                "materialType": "product_image",
                "prompt": "",
                "negativePrompt": "",
                "editInstruction": "Smooth fade in, golden light sweep across the dress, premium look",
            },
            {
                "shotNo": 3, "duration": 5,
                "scene": "模特穿上裙子在阳光充足的街景中",
                "action": "模特自然行走、转身、展示裙摆飘逸",
                "subtitle": "法式茶歇裙，显瘦又高级",
                "materialType": "ai_video",
                "prompt": "Fashion model wearing a-line floral dress walking on sunlit street, dress flowing naturally, confident smile, golden hour lighting",
                "negativePrompt": "blurry, deformed body, extra limbs, watermark, dark lighting",
                "editInstruction": "Slow motion at 0.8x, warm color grading, focus on dress movement",
            },
            {
                "shotNo": 4, "duration": 4,
                "scene": "面料细节和做工特写",
                "action": "镜头缓慢滑过领口、腰线、裙摆细节",
                "subtitle": "细节控狂喜！",
                "materialType": "product_image_motion",
                "prompt": "",
                "negativePrompt": "",
                "editInstruction": "Ken Burns slow zoom on product details, soft focus background",
            },
            {
                "shotNo": 5, "duration": 5,
                "scene": "同一模特多种场景切换——咖啡厅、公园、海滩",
                "action": "快速场景切换展示裙子的百搭性",
                "subtitle": "一条裙子，三种场合",
                "materialType": "ai_video",
                "prompt": "Same model wearing same floral dress in three different settings: cafe, park, beach. Seamless transition between scenes",
                "negativePrompt": "different dress, inconsistent lighting, jumpy transition",
                "editInstruction": "Match-cut transitions between scenes, keep model position consistent",
            },
            {
                "shotNo": 6, "duration": 3,
                "scene": "模特对着镜头微笑，指向购物链接",
                "action": "模特走近镜头，手指向画面下方的链接",
                "subtitle": "链接在下面，先到先得！",
                "materialType": "ai_video",
                "prompt": "Fashion model smiling warmly at camera, pointing down toward shopping link, clean background, well-lit",
                "negativePrompt": "blurry face, distorted features, scary expression",
                "editInstruction": "Freeze frame on final smile, text overlay animation for CTA",
            },
        ],
    },
    "keyframe_prompts": {
        "prompts": [
            {"shotNo": 1, "purpose": "first_frame", "prompt": "Young woman in messy bedroom staring at overflowing closet, frustrated morning mood, soft natural light from window, candid shot", "negativePrompt": "blurry, low quality, deformed face, watermark"},
            {"shotNo": 2, "purpose": "first_frame", "prompt": "Beautiful floral a-line dress on wooden hanger against clean white wall, boutique product photography, soft golden rim light", "negativePrompt": "wrinkled fabric, cluttered background, harsh shadows, watermark"},
            {"shotNo": 3, "purpose": "first_frame", "prompt": "Fashion model in floral dress walking on cobblestone street, Parisian architecture background, golden hour sunlight, flowing fabric", "negativePrompt": "blurry, deformed body, extra limbs, modern cars, watermark"},
            {"shotNo": 4, "purpose": "product_detail", "prompt": "Macro close-up of floral cotton fabric texture, visible weave pattern, soft focus depth of field, premium quality feel", "negativePrompt": "blurry, low resolution, synthetic-looking fabric"},
            {"shotNo": 5, "purpose": "first_frame", "prompt": "Split screen composition: same model in cafe, park, and beach wearing identical floral dress, seamless color grading", "negativePrompt": "different outfits, inconsistent model appearance, harsh transitions"},
            {"shotNo": 6, "purpose": "reference", "prompt": "Fashion model smiling warmly at camera, clean pastel background, soft beauty lighting, genuine happy expression", "negativePrompt": "blurry face, distorted smile, scary expression, harsh shadows"},
        ],
    },
    "fake_keyframes": {
        "keyframes": [
            {"shotNo": 1, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes/shot1_front.png", "prompt": "Young woman in messy bedroom...", "provider": "fake", "modelName": "fake-v1", "qualityScore": 85, "source": "ai_generated", "imagePurpose": "first_frame"},
            {"shotNo": 2, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes/shot2_dress.png", "prompt": "Beautiful floral a-line dress on hanger...", "provider": "fake", "modelName": "fake-v1", "qualityScore": 90, "source": "ai_generated", "imagePurpose": "first_frame"},
            {"shotNo": 3, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes/shot3_model.png", "prompt": "Fashion model in floral dress walking...", "provider": "fake", "modelName": "fake-v1", "qualityScore": 88, "source": "ai_generated", "imagePurpose": "first_frame"},
            {"shotNo": 4, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes/shot4_detail.png", "prompt": "Macro close-up of floral cotton fabric...", "provider": "fake", "modelName": "fake-v1", "qualityScore": 92, "source": "ai_generated", "imagePurpose": "product_detail"},
            {"shotNo": 5, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes/shot5_split.png", "prompt": "Split screen composition...", "provider": "fake", "modelName": "fake-v1", "qualityScore": 80, "source": "ai_generated", "imagePurpose": "first_frame"},
            {"shotNo": 6, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/keyframes/shot6_cta.png", "prompt": "Fashion model smiling warmly at camera...", "provider": "fake", "modelName": "fake-v1", "qualityScore": 86, "source": "ai_generated", "imagePurpose": "reference"},
        ]
    },
    "video_clip_prompts": {
        "prompts": [
            {"shotNo": 1, "prompt": "Young woman in messy bedroom looking frustrated at clothes, candid handheld camera movement, soft morning lighting, 3 seconds", "negativePrompt": "blurry, stabilized, perfect lighting, studio look"},
            {"shotNo": 2, "prompt": "Dress reveal with golden light sweep animation, fabric gently swaying, premium product showcase, 2 seconds", "negativePrompt": "static image, harsh lighting, wrinkles"},
            {"shotNo": 3, "prompt": "Model walking confidently on sunlit Parisian street, dress flowing naturally, slow motion, golden hour, 5 seconds", "negativePrompt": "blurry, deformed body, modern elements, dark"},
            {"shotNo": 4, "prompt": "Ken Burns slow zoom over dress details, fabric texture visible, soft focus background, 4 seconds", "negativePrompt": "static, blurry fabric, synthetic look"},
            {"shotNo": 5, "prompt": "Match-cut transition between cafe, park, beach scenes with same model and dress, seamless morph effect, 5 seconds", "negativePrompt": "jumpy cuts, different outfits, inconsistent lighting"},
            {"shotNo": 6, "prompt": "Model smiling warmly, natural expression, clean background, walking toward camera, 3 seconds", "negativePrompt": "distorted face, creepy smile, stiff movement"},
        ],
    },
    "fake_video_clips": {
        "clips": [
            {"shotNo": 1, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/clips/shot1.mp4", "prompt": "Young woman in messy bedroom...", "provider": "fake", "modelName": "fake-v1", "duration": 3, "qualityScore": 84, "source": "ai_generated"},
            {"shotNo": 2, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/clips/shot2.mp4", "prompt": "Dress reveal with golden light...", "provider": "fake", "modelName": "fake-v1", "duration": 2, "qualityScore": 90, "source": "ai_generated"},
            {"shotNo": 3, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/clips/shot3.mp4", "prompt": "Model walking confidently...", "provider": "fake", "modelName": "fake-v1", "duration": 5, "qualityScore": 87, "source": "ai_generated"},
            {"shotNo": 4, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/clips/shot4.mp4", "prompt": "Ken Burns slow zoom...", "provider": "fake", "modelName": "fake-v1", "duration": 4, "qualityScore": 91, "source": "ai_generated"},
            {"shotNo": 5, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/clips/shot5.mp4", "prompt": "Match-cut transition...", "provider": "fake", "modelName": "fake-v1", "duration": 5, "qualityScore": 79, "source": "ai_generated"},
            {"shotNo": 6, "status": "completed", "url": "https://placeholder.cos.ap-guangzhou.myqcloud.com/tk-ai-video/fashion/clips/shot6.mp4", "prompt": "Model smiling warmly...", "provider": "fake", "modelName": "fake-v1", "duration": 3, "qualityScore": 88, "source": "ai_generated"},
        ]
    },
    "feedback_classification": {
        "feedbackCategory": "visual_quality",
        "targetType": "video_clip",
        "strategy": "regenerate_video_clip",
        "affectedShots": [3],
        "repairNotes": "The fabric detail in shot 3 looks blurry — regenerating with higher resolution and better lighting prompt.",
        "preserveConstraints": {
            "productDetails": ["floral print", "a-line silhouette", "knee-length"],
            "styleAttributes": ["Casual", "Bohemian", "Lightweight"],
        },
        "estimatedCostTier": "cheap",
        "requiresUserConfirmation": False,
    },
    "repair_plan": {
        "feedbackCategory": "visual_quality",
        "targetType": "video_clip",
        "strategy": "regenerate_video_clip",
        "affectedShots": [3],
        "repairNotes": "Executing repair: regenerating video clip for shot 3 with improved prompt emphasizing fabric sharpness and golden hour lighting. One clip affected, cheap cost tier.",
        "preserveConstraints": {
            "productDetails": ["floral print", "a-line silhouette", "knee-length"],
            "styleAttributes": ["Casual", "Bohemian", "Lightweight"],
        },
        "newPrompt": "Fashion model walking on sunlit street, floral dress flowing, fabric texture extremely sharp and detailed, golden hour backlight, 5 seconds, cinematic quality, 4K",
        "estimatedCostTier": "cheap",
        "requiresUserConfirmation": False,
    },
}


def get_fashion_fixture(task_type: str) -> dict:
    """Return a deep copy of a fashion fixture dict (safe to mutate)."""
    import copy
    fixture = FASHION_FIXTURES.get(task_type)
    if fixture is None:
        raise ValueError(f"No fashion fixture defined for task_type: {task_type}")
    return copy.deepcopy(fixture)


TEXT_TASK_TYPES = {
    "product_analysis",
    "video_plans",
    "script",
    "storyboard",
    "quality_check",
    # Fashion Creative Loop V1 text tasks
    "fashion_asset_analysis",
    "reference_video_analysis",
    "fashion_plans",
    "fashion_storyboard",
}


def _text_llm_api_key() -> str:
    return settings.text_llm_api_key or settings.openai_api_key or settings.anthropic_api_key


def _is_fake_mode(task_type: str) -> bool:
    if settings.force_fake_llm:
        return True
    if task_type == "materials":
        return not (settings.enable_image_generation or settings.enable_video_generation)
    return not _text_llm_api_key()


async def call_llm(task_type: str, system_prompt: str, user_prompt: str, model: str = "gpt-4o") -> dict:
    """Call the configured text LLM. In fake mode, returns fixture data."""
    correlation_id = ""  # set from activity context

    if _is_fake_mode(task_type):
        fixture = FIXTURES.get(task_type) or FASHION_FIXTURES.get(task_type)
        if fixture is None:
            raise ValueError(f"No fixture defined for task_type: {task_type}")
        log.info("FAKE LLM call: task_type=%s, model=%s", task_type, model)
        return fixture

    if task_type not in TEXT_TASK_TYPES:
        raise RuntimeError(
            f"task_type={task_type} is not a text LLM task. "
            "Use image/video generation services for paid media generation."
        )

    text_model = model if model != "gpt-4o" else (settings.text_llm_model or settings.vision_llm_model)
    provider = settings.text_llm_provider.lower()

    if provider in {"openai", "openai_compatible"}:
        return await _call_openai(task_type, system_prompt, user_prompt, text_model)
    if provider == "anthropic":
        return await _call_anthropic(task_type, system_prompt, user_prompt, text_model)

    raise RuntimeError(f"Unsupported TEXT_LLM_PROVIDER: {settings.text_llm_provider}")


async def call_llm_with_images(
    task_type: str,
    system_prompt: str,
    user_prompt: str,
    image_urls: list[str],
    model: str = "gpt-4o",
) -> dict:
    """Call an OpenAI-compatible vision model with image_url inputs."""
    if _is_fake_mode(task_type):
        fixture = FIXTURES.get(task_type) or FASHION_FIXTURES.get(task_type)
        if fixture is None:
            raise ValueError(f"No fixture defined for task_type: {task_type}")
        log.info("FAKE vision LLM call: task_type=%s, model=%s", task_type, model)
        return fixture

    if task_type not in TEXT_TASK_TYPES:
        raise RuntimeError(
            f"task_type={task_type} is not a text/vision LLM task. "
            "Use image/video generation services for paid media generation."
        )

    provider = (settings.vision_llm_provider or settings.text_llm_provider).lower()
    vision_model = settings.vision_llm_model or model
    if vision_model == "gpt-4o":
        vision_model = settings.text_llm_model
    clean_image_urls = [url for url in image_urls if isinstance(url, str) and url.strip()]

    if provider not in {"openai", "openai_compatible"}:
        raise RuntimeError(
            f"Vision input is only wired for OpenAI-compatible providers, got: {settings.text_llm_provider}"
        )

    return await _call_openai_vision(task_type, system_prompt, user_prompt, clean_image_urls, vision_model)


async def _call_openai(task_type: str, system_prompt: str, user_prompt: str, model: str) -> dict:
    try:
        from openai import AsyncOpenAI
    except ImportError:
        raise RuntimeError("openai package not installed")

    api_key = settings.text_llm_api_key or settings.vision_llm_api_key or settings.openai_api_key
    base_url = settings.text_llm_base_url or settings.vision_llm_base_url or settings.openai_base_url or None
    client = AsyncOpenAI(
        api_key=api_key,
        base_url=base_url,
        timeout=settings.text_llm_timeout_seconds,
    )
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]
    response = await _create_chat_completion(client, model, messages)
    content = response.choices[0].message.content or "{}"
    tokens_in = response.usage.prompt_tokens if response.usage else 0
    tokens_out = response.usage.completion_tokens if response.usage else 0

    log.info("Text LLM call: task_type=%s, model=%s, tokens_in=%d, tokens_out=%d",
             task_type, model, tokens_in, tokens_out)
    return _parse_llm_json(content)


async def _call_openai_vision(
    task_type: str,
    system_prompt: str,
    user_prompt: str,
    image_urls: list[str],
    model: str,
) -> dict:
    try:
        from openai import AsyncOpenAI
    except ImportError:
        raise RuntimeError("openai package not installed")

    api_key = settings.vision_llm_api_key or settings.text_llm_api_key or settings.openai_api_key
    base_url = settings.vision_llm_base_url or settings.text_llm_base_url or settings.openai_base_url or None
    client = AsyncOpenAI(
        api_key=api_key,
        base_url=base_url,
        timeout=settings.text_llm_timeout_seconds,
    )

    prepared_image_urls = await _prepare_vision_image_urls(image_urls)

    content: list[dict] = [{"type": "text", "text": user_prompt}]
    for url in prepared_image_urls:
        content.append({"type": "image_url", "image_url": {"url": url}})

    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": content},
    ]
    log.info(
        "Vision LLM request: task_type=%s, model=%s, images=%d, timeout=%ss",
        task_type,
        model,
        len(prepared_image_urls),
        settings.text_llm_timeout_seconds,
    )
    response = await _create_chat_completion(client, model, messages)
    content_text = response.choices[0].message.content or "{}"
    tokens_in = response.usage.prompt_tokens if response.usage else 0
    tokens_out = response.usage.completion_tokens if response.usage else 0

    log.info(
        "Vision LLM call: task_type=%s, model=%s, images=%d, tokens_in=%d, tokens_out=%d",
        task_type, model, len(prepared_image_urls), tokens_in, tokens_out,
    )
    return _parse_llm_json(content_text)


async def _prepare_vision_image_urls(image_urls: list[str]) -> list[str]:
    """Prepare image inputs for OpenAI-compatible vision APIs.

    Many providers can accept remote image URLs, but they fetch those URLs from the
    provider side. E-commerce image hosts often block unknown data-center fetches,
    which causes long hangs. By default we download images locally and send data
    URLs so the vision model receives the actual bytes.
    """
    if not settings.vision_llm_inline_images:
        return image_urls

    prepared: list[str] = []
    failures: list[str] = []
    for url in image_urls:
        if url.startswith("data:"):
            prepared.append(url)
            continue
        try:
            prepared.append(await _download_image_as_data_url(url))
        except Exception as exc:
            failures.append(f"{_safe_url_for_log(url)}: {exc}")

    if failures:
        log.warning("Failed to inline %d vision image(s): %s", len(failures), "; ".join(failures))

    if image_urls and not prepared:
        raise RuntimeError(
            "No vision images could be downloaded for inline analysis. "
            "Check that the task asset image URL is publicly reachable from the Python service."
        )

    return prepared


async def _download_image_as_data_url(url: str) -> str:
    timeout = settings.vision_llm_image_download_timeout_seconds
    async with httpx.AsyncClient(
        timeout=httpx.Timeout(timeout),
        follow_redirects=True,
        headers={
            "User-Agent": "Mozilla/5.0 (compatible; TK-AI-Video-Orchestrator/1.0)",
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
        },
    ) as client:
        response = await client.get(url)
        response.raise_for_status()
        data = response.content

    max_bytes = settings.vision_llm_max_image_bytes
    if len(data) > max_bytes:
        raise RuntimeError(f"image is too large: {len(data)} bytes > {max_bytes} bytes")

    content_type = response.headers.get("content-type", "").split(";")[0].strip().lower()
    if not content_type or not content_type.startswith("image/"):
        parsed_path = urlparse(url).path.lower()
        if parsed_path.endswith(".png"):
            content_type = "image/png"
        elif parsed_path.endswith(".webp"):
            content_type = "image/webp"
        elif parsed_path.endswith(".gif"):
            content_type = "image/gif"
        else:
            content_type = "image/jpeg"

    encoded = base64.b64encode(data).decode("ascii")
    return f"data:{content_type};base64,{encoded}"


def _safe_url_for_log(url: str) -> str:
    parsed = urlparse(url)
    safe = f"{parsed.scheme}://{parsed.netloc}{parsed.path}"
    return safe[:160]


async def _create_chat_completion(client, model: str, messages: list[dict]):
    """Create a JSON chat completion, retrying without response_format for providers that reject it."""
    try:
        return await asyncio.wait_for(
            client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=0.7,
                response_format={"type": "json_object"},
            ),
            timeout=settings.text_llm_timeout_seconds,
        )
    except (asyncio.TimeoutError, TimeoutError) as exc:
        raise RuntimeError(
            f"LLM request timed out after {settings.text_llm_timeout_seconds:g}s: model={model}"
        ) from exc
    except Exception as exc:
        message = str(exc).lower()
        if "response_format" not in message and "json_object" not in message:
            raise
        log.warning("Provider rejected response_format=json_object; retrying without response_format: %s", exc)
        try:
            return await asyncio.wait_for(
                client.chat.completions.create(
                    model=model,
                    messages=messages,
                    temperature=0.7,
                ),
                timeout=settings.text_llm_timeout_seconds,
            )
        except (asyncio.TimeoutError, TimeoutError) as timeout_exc:
            raise RuntimeError(
                f"LLM request timed out after {settings.text_llm_timeout_seconds:g}s: model={model}"
            ) from timeout_exc


async def _call_anthropic(task_type: str, system_prompt: str, user_prompt: str, model: str) -> dict:
    try:
        from anthropic import AsyncAnthropic
    except ImportError:
        raise RuntimeError("anthropic package not installed")

    api_key = settings.text_llm_api_key or settings.anthropic_api_key
    base_url = settings.text_llm_base_url or None
    client = AsyncAnthropic(api_key=api_key, base_url=base_url)
    response = await client.messages.create(
        model=model or "claude-sonnet-4-6",
        max_tokens=4096,
        system=system_prompt,
        messages=[{"role": "user", "content": user_prompt}],
    )
    # DeepSeek and other providers may return ThinkingBlock alongside TextBlock.
    # Extract text from the first TextBlock, skipping thinking/reasoning blocks.
    content_text = "{}"
    if response.content:
        for block in response.content:
            if hasattr(block, "text"):
                content_text = block.text
                break
    tokens_in = response.usage.input_tokens if response.usage else 0
    tokens_out = response.usage.output_tokens if response.usage else 0

    log.info("LLM call: task_type=%s, model=%s, tokens_in=%d, tokens_out=%d",
             task_type, model, tokens_in, tokens_out)
    return _parse_llm_json(content_text)


def _parse_llm_json(raw: str) -> dict:
    """Strip markdown fences and parse JSON."""
    text = raw.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        lines = [l for l in lines if not l.startswith("```")]
        text = "\n".join(lines)
    return json.loads(text)
