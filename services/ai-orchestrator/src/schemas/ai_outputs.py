"""Pydantic models for AI structured outputs.

These map to the contracts defined in docs/03-ai-output-json-schema.md.
V1 6 AI output schemas: ProductAnalysis, VideoPlanResult, ScriptOutput,
StoryboardResult, MaterialResult, QualityCheckResult, RenderManifest.
"""

from pydantic import BaseModel, Field
from typing import Optional
from uuid import UUID


class ProductAnalysis(BaseModel):
    """AI 商品分析结果."""
    category: str
    sellingPoints: list[str]
    painPoints: list[str]
    targetAudience: list[str]
    scenes: list[str]
    recommendedVideoTypes: list[str]
    videoScore: int = Field(ge=0, le=100)
    riskTips: list[str] = Field(default_factory=list)
    complianceNotes: str = ""


class VideoPlanItem(BaseModel):
    """单个视频方案."""
    type: str
    title: str
    hook: str
    structure: str
    reason: str
    estimatedDuration: int
    score: int = Field(ge=0, le=100)


class VideoPlanResult(BaseModel):
    """3-5 个视频方案."""
    plans: list[VideoPlanItem] = Field(min_length=3, max_length=5)


class ScriptOutput(BaseModel):
    """视频脚本."""
    title: str
    hook: str
    script: str
    caption: str
    hashtags: list[str]


class ShotItem(BaseModel):
    """单个分镜."""
    shotNo: int
    duration: int = Field(gt=0)
    scene: str
    action: str = ""
    subtitle: str
    materialType: str
    prompt: str = ""
    negativePrompt: str = ""
    editInstruction: str = ""


class StoryboardResult(BaseModel):
    """4-12 个分镜镜头."""
    title: str
    hook: str
    coverText: str = ""
    caption: str
    hashtags: list[str]
    musicSuggestion: str = ""
    shots: list[ShotItem] = Field(min_length=4, max_length=12)


class MaterialItem(BaseModel):
    """单个 AI 生成的素材."""
    shotNo: int
    type: str
    url: str
    prompt: str
    provider: str = ""
    modelName: str = ""
    qualityScore: Optional[int] = Field(default=None, ge=0, le=100)


class MaterialResult(BaseModel):
    """素材生成结果."""
    materials: list[MaterialItem]


class QualityCheckResult(BaseModel):
    """质量检查结果."""
    passed: bool
    score: int = Field(ge=0, le=100)
    issues: list[str] = Field(default_factory=list)
    riskScore: int = Field(ge=0, le=100)
    hookQualityScore: int = Field(ge=0, le=100)
    productVisibilityScore: int = Field(ge=0, le=100)
    subtitleReadabilityScore: int = Field(ge=0, le=100)
    sensitiveContentFlag: bool = False


class CallbackPayload(BaseModel):
    """AI callback payload to Java."""
    taskId: UUID
    schemaVersion: str = "1.0.0"
    stage: str
    status: str  # success | failed
    nextTaskStatus: str
    correlationId: str
    # Stage-specific payloads
    productAnalysis: Optional[dict] = None
    plans: Optional[list[dict]] = None
    storyboard: Optional[dict] = None
    materials: Optional[list[dict]] = None
    renderManifest: Optional[dict] = None
    qualityCheck: Optional[dict] = None
    error: Optional[dict] = None
