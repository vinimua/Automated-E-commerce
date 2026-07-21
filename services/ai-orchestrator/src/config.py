"""Application configuration — loaded from environment variables."""

import os
from dataclasses import dataclass, field

from dotenv import load_dotenv


load_dotenv()


@dataclass
class Settings:
    # Temporal (腾讯云服务器)
    temporal_host: str = os.getenv("TEMPORAL_HOST", "124.223.200.16:17233")
    temporal_namespace: str = os.getenv("TEMPORAL_NAMESPACE", "default")
    temporal_task_queue: str = os.getenv("TEMPORAL_TASK_QUEUE", "ai-video-task-queue")

    # Java API (for callbacks)
    java_api_base_url: str = os.getenv("JAVA_API_BASE_URL", "http://localhost:8080")
    internal_service_token: str = os.getenv(
        "INTERNAL_SERVICE_TOKEN", "internal-dev-token-change-in-production"
    )

    # AI Providers
    text_llm_provider: str = os.getenv("TEXT_LLM_PROVIDER", "openai")
    text_llm_model: str = os.getenv("TEXT_LLM_MODEL", "gpt-4o-mini")
    text_llm_api_key: str = os.getenv("TEXT_LLM_API_KEY", "")
    text_llm_base_url: str = os.getenv("TEXT_LLM_BASE_URL", "")
    text_llm_timeout_seconds: float = float(os.getenv("TEXT_LLM_TIMEOUT_SECONDS", "120"))
    vision_llm_provider: str = os.getenv("VISION_LLM_PROVIDER", "")
    vision_llm_model: str = os.getenv("VISION_LLM_MODEL", "")
    vision_llm_api_key: str = os.getenv("VISION_LLM_API_KEY", "")
    vision_llm_base_url: str = os.getenv("VISION_LLM_BASE_URL", "")
    vision_llm_inline_images: bool = os.getenv("VISION_LLM_INLINE_IMAGES", "true").lower() == "true"
    vision_llm_image_download_timeout_seconds: float = float(
        os.getenv("VISION_LLM_IMAGE_DOWNLOAD_TIMEOUT_SECONDS", "20")
    )
    vision_llm_max_image_bytes: int = int(os.getenv("VISION_LLM_MAX_IMAGE_BYTES", str(8 * 1024 * 1024)))
    force_fake_llm: bool = os.getenv("FORCE_FAKE_LLM", "false").lower() == "true"

    # Legacy provider envs are still supported as fallbacks.
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "")
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")

    # Image / Video Gen APIs
    image_gen_provider: str = os.getenv("IMAGE_GEN_PROVIDER", "openai")
    image_gen_api_key: str = os.getenv("IMAGE_GEN_API_KEY", "")
    image_gen_base_url: str = os.getenv("IMAGE_GEN_BASE_URL", "")
    image_gen_model: str = os.getenv("IMAGE_GEN_MODEL", "dall-e-3")
    video_gen_provider: str = os.getenv("VIDEO_GEN_PROVIDER", "volcengine")
    video_gen_model: str = os.getenv("VIDEO_GEN_MODEL", "doubao-seedance-1-5-pro-251215")
    video_gen_api_key: str = os.getenv("VIDEO_GEN_API_KEY", "")
    video_gen_base_url: str = os.getenv("VIDEO_GEN_BASE_URL", "")
    enable_image_generation: bool = os.getenv("ENABLE_IMAGE_GENERATION", "false").lower() == "true"
    enable_video_generation: bool = os.getenv("ENABLE_VIDEO_GENERATION", "false").lower() == "true"
    enable_langgraph_repair: bool = os.getenv("ENABLE_LANGGRAPH_REPAIR", "false").lower() == "true"
    max_image_assets_per_task: int = int(os.getenv("MAX_IMAGE_ASSETS_PER_TASK", "4"))
    max_video_clips_per_task: int = int(os.getenv("MAX_VIDEO_CLIPS_PER_TASK", "0"))
    video_gen_require_approval: bool = (
        os.getenv("VIDEO_GEN_REQUIRE_APPROVAL", "true").lower() == "true"
    )

    # Cost limit per task (USD)
    ai_cost_limit_per_task: float = float(os.getenv("AI_COST_LIMIT_PER_TASK", "5.0"))

    # Retry
    max_json_repair_retries: int = int(os.getenv("MAX_JSON_REPAIR_RETRIES", "3"))
    max_model_retries: int = int(os.getenv("MAX_MODEL_RETRIES", "2"))

    # Schema
    schema_version: str = "1.0.0"
    max_callback_retries: int = int(os.getenv("MAX_CALLBACK_RETRIES", "3"))


settings = Settings()
