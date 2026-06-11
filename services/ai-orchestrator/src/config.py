"""Application configuration — loaded from environment variables."""

import os
from dataclasses import dataclass, field


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
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_base_url: str = os.getenv("OPENAI_BASE_URL", "")
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")

    # Image / Video Gen APIs
    image_gen_provider: str = os.getenv("IMAGE_GEN_PROVIDER", "openai")
    video_gen_provider: str = os.getenv("VIDEO_GEN_PROVIDER", "openai")

    # Cost limit per task (USD)
    ai_cost_limit_per_task: float = float(os.getenv("AI_COST_LIMIT_PER_TASK", "5.0"))

    # Retry
    max_json_repair_retries: int = int(os.getenv("MAX_JSON_REPAIR_RETRIES", "3"))
    max_model_retries: int = int(os.getenv("MAX_MODEL_RETRIES", "2"))

    # Schema
    schema_version: str = "1.0.0"
    max_callback_retries: int = int(os.getenv("MAX_CALLBACK_RETRIES", "3"))


settings = Settings()
