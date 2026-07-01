"""LLM service — OpenAI/Anthropic wrapper with fake provider for dev."""

import json
import os
import logging
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


TEXT_TASK_TYPES = {
    "product_analysis",
    "video_plans",
    "script",
    "storyboard",
    "quality_check",
}


def _text_llm_api_key() -> str:
    return settings.text_llm_api_key or settings.openai_api_key or settings.anthropic_api_key


def _is_fake_mode(task_type: str) -> bool:
    if task_type == "materials":
        return not (settings.enable_image_generation or settings.enable_video_generation)
    return not _text_llm_api_key()


async def call_llm(task_type: str, system_prompt: str, user_prompt: str, model: str = "gpt-4o") -> dict:
    """Call the configured text LLM. In fake mode, returns fixture data."""
    correlation_id = ""  # set from activity context

    if _is_fake_mode(task_type):
        fixture = FIXTURES.get(task_type)
        if fixture is None:
            raise ValueError(f"No fixture defined for task_type: {task_type}")
        log.info("FAKE LLM call: task_type=%s, model=%s", task_type, model)
        return fixture

    if task_type not in TEXT_TASK_TYPES:
        raise RuntimeError(
            f"task_type={task_type} is not a text LLM task. "
            "Use image/video generation services for paid media generation."
        )

    text_model = model if model != "gpt-4o" else settings.text_llm_model
    provider = settings.text_llm_provider.lower()

    if provider in {"openai", "openai_compatible"}:
        return await _call_openai(task_type, system_prompt, user_prompt, text_model)
    if provider == "anthropic":
        return await _call_anthropic(task_type, system_prompt, user_prompt, text_model)

    raise RuntimeError(f"Unsupported TEXT_LLM_PROVIDER: {settings.text_llm_provider}")


async def _call_openai(task_type: str, system_prompt: str, user_prompt: str, model: str) -> dict:
    try:
        from openai import AsyncOpenAI
    except ImportError:
        raise RuntimeError("openai package not installed")

    api_key = settings.text_llm_api_key or settings.openai_api_key
    base_url = settings.text_llm_base_url or settings.openai_base_url or None
    client = AsyncOpenAI(api_key=api_key, base_url=base_url)
    response = await client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
        temperature=0.7,
        response_format={"type": "json_object"},
    )
    content = response.choices[0].message.content or "{}"
    tokens_in = response.usage.prompt_tokens if response.usage else 0
    tokens_out = response.usage.completion_tokens if response.usage else 0

    log.info("LLM call: task_type=%s, model=%s, tokens_in=%d, tokens_out=%d",
             task_type, model, tokens_in, tokens_out)
    return _parse_llm_json(content)


async def _call_anthropic(task_type: str, system_prompt: str, user_prompt: str, model: str) -> dict:
    try:
        from anthropic import AsyncAnthropic
    except ImportError:
        raise RuntimeError("anthropic package not installed")

    api_key = settings.text_llm_api_key or settings.anthropic_api_key
    client = AsyncAnthropic(api_key=api_key)
    response = await client.messages.create(
        model=model or "claude-sonnet-4-6",
        max_tokens=4096,
        system=system_prompt,
        messages=[{"role": "user", "content": user_prompt}],
    )
    content = response.content[0].text if response.content else "{}"
    tokens_in = response.usage.input_tokens if response.usage else 0
    tokens_out = response.usage.output_tokens if response.usage else 0

    log.info("LLM call: task_type=%s, model=%s, tokens_in=%d, tokens_out=%d",
             task_type, model, tokens_in, tokens_out)
    return _parse_llm_json(content)


def _parse_llm_json(raw: str) -> dict:
    """Strip markdown fences and parse JSON."""
    text = raw.strip()
    if text.startswith("```"):
        lines = text.split("\n")
        lines = [l for l in lines if not l.startswith("```")]
        text = "\n".join(lines)
    return json.loads(text)
