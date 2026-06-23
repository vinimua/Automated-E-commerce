"""Pydantic models for AI structured outputs — matches docs/03-ai-output-json-schema.md exactly."""

from pydantic import BaseModel, Field, model_validator
from typing import Literal, Optional
from uuid import UUID

# ── Shared type aliases ──────────────────────────────────────────

VideoTypeEnum = Literal[
    "pain_point_solution", "before_after", "review",
    "product_showcase", "ugc_style", "tutorial",
]

MaterialTypeEnum = Literal[
    "product_image", "product_image_motion",
    "ai_image", "ai_video", "text_animation", "uploaded_video",
]

MaterialAssetTypeEnum = Literal[
    "image", "video", "product_image", "cover_image", "audio", "subtitle",
]

CallbackStageEnum = Literal[
    "product_analysis", "video_plan", "storyboard",
    "material", "quality_check", "render_manifest",
]

CallbackStatusEnum = Literal["success", "failed"]

NextTaskStatusEnum = Literal[
    "analysis_completed", "plan_generated", "script_generated",
    "material_generated", "checking", "rendering", "failed",
]


# ── 3. ProductAnalysis ──────────────────────────────────────────

class ProductAnalysis(BaseModel):
    """AI 商品分析结果."""
    model_config = {"extra": "forbid"}

    category: str = Field(min_length=1)
    sellingPoints: list[str] = Field(min_length=1)
    painPoints: list[str] = Field(min_length=1)
    targetAudience: list[str] = Field(min_length=1)
    scenes: list[str] = Field(min_length=1)
    recommendedVideoTypes: list[VideoTypeEnum] = Field(min_length=1)
    videoScore: int = Field(ge=0, le=100)
    riskTips: list[str] = Field(default_factory=list)
    claimRiskLevel: Optional[Literal["low", "medium", "high"]] = None
    forbiddenClaims: list[str] = Field(default_factory=list)
    complianceTips: list[str] = Field(default_factory=list)
    needsHumanReview: bool = False


# ── 4. VideoPlanResult ──────────────────────────────────────────

class VideoPlanItem(BaseModel):
    """单个视频方案."""
    model_config = {"extra": "forbid"}

    type: VideoTypeEnum
    title: str = Field(min_length=1)
    hook: str = Field(min_length=1, max_length=120)
    structure: str = Field(min_length=1)
    reason: str = Field(min_length=1)
    estimatedDuration: int = Field(ge=15, le=30)
    score: int = Field(ge=0, le=100)


class VideoPlanResult(BaseModel):
    """3-5 个视频方案."""
    model_config = {"extra": "forbid"}

    plans: list[VideoPlanItem] = Field(min_length=3, max_length=5)


# ── 5. StoryboardResult ────────────────────────────────────────

class ShotItem(BaseModel):
    """单个分镜镜头."""
    model_config = {"extra": "forbid"}

    shotNo: int = Field(ge=1)
    duration: int = Field(ge=1, le=8)
    scene: str = Field(min_length=1)
    action: str = ""
    subtitle: str = Field(min_length=1, max_length=90)
    materialType: MaterialTypeEnum
    prompt: str = ""
    negativePrompt: str = ""
    editInstruction: str = ""

    @model_validator(mode="after")
    def prompt_required_for_ai(self) -> "ShotItem":
        if self.materialType in ("ai_image", "ai_video") and not self.prompt:
            raise ValueError(f"prompt is required when materialType is '{self.materialType}'")
        return self


class StoryboardResult(BaseModel):
    """分镜结果（4-12 个镜头)."""
    model_config = {"extra": "forbid"}

    title: str = Field(min_length=1, max_length=120)
    hook: str = Field(min_length=1, max_length=120)
    duration: int = Field(ge=15, le=30)
    caption: str = Field(min_length=1, max_length=500)
    hashtags: list[str] = Field(min_length=1, max_length=10)
    coverText: str = Field(min_length=1, max_length=80)
    musicSuggestion: str = Field(min_length=1)
    shots: list[ShotItem] = Field(min_length=4, max_length=12)


# ── 6. MaterialResult ──────────────────────────────────────────

class MaterialItem(BaseModel):
    """单个 AI 生成的素材."""
    model_config = {"extra": "forbid"}

    shotNo: int = Field(ge=1)
    type: MaterialAssetTypeEnum
    status: Literal["completed", "failed"]
    provider: str
    url: Optional[str] = None
    prompt: Optional[str] = None
    negativePrompt: Optional[str] = None
    modelName: Optional[str] = None
    qualityScore: Optional[int] = Field(default=None, ge=0, le=100)
    errorMessage: Optional[str] = None

    @model_validator(mode="after")
    def conditional_fields(self) -> "MaterialItem":
        if self.status == "completed" and not self.url:
            raise ValueError("url is required when status is 'completed'")
        if self.status == "failed" and not self.errorMessage:
            raise ValueError("errorMessage is required when status is 'failed'")
        return self


class MaterialResult(BaseModel):
    """素材生成结果."""
    model_config = {"extra": "forbid"}

    materials: list[MaterialItem] = Field(min_length=1)


# ── 7. QualityCheckResult ──────────────────────────────────────

class QualityChecks(BaseModel):
    """质量检查子项."""
    model_config = {"extra": "forbid"}

    hasHook: bool
    productAppearsEarly: bool
    subtitleReadable: bool
    noSensitiveClaims: bool
    visualQualityAcceptable: bool


class QualityCheckResult(BaseModel):
    """质量检查结果."""
    model_config = {"extra": "forbid"}

    qualityScore: int = Field(ge=0, le=100)
    riskScore: int = Field(ge=0, le=100)
    checks: QualityChecks
    complianceTips: list[str] = Field(default_factory=list)
    forbiddenClaims: list[str] = Field(default_factory=list)
    needsHumanReview: bool = False
    suggestions: list[str] = Field(default_factory=list)


# ── 8. AiCallbackPayload ───────────────────────────────────────

class CallbackError(BaseModel):
    """回调错误信息."""
    model_config = {"extra": "forbid"}

    errorCode: str
    errorMessage: str
    failedStage: str
    retryable: bool
    provider: Optional[str] = None
    rawError: Optional[dict] = None


class CallbackPayload(BaseModel):
    """AI callback payload sent to Java."""
    model_config = {"extra": "forbid"}

    taskId: UUID
    schemaVersion: str = Field(default="1.0.0", pattern=r"^1\.0\.0$")
    stage: CallbackStageEnum
    status: CallbackStatusEnum
    nextTaskStatus: Optional[NextTaskStatusEnum] = None

    productAnalysis: Optional[dict] = None
    plans: Optional[list[dict]] = None
    storyboard: Optional[dict] = None
    materials: Optional[list[dict]] = None
    qualityCheck: Optional[dict] = None
    renderManifest: Optional[dict] = None

    error: Optional[CallbackError] = None

    @model_validator(mode="after")
    def error_required_on_failed(self) -> "CallbackPayload":
        if self.status == "failed" and self.error is None:
            raise ValueError("error is required when status is 'failed'")
        return self
