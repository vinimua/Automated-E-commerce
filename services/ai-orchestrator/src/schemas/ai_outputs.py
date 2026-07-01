"""Pydantic models for AI structured outputs.

These models are the Python runtime version of docs/03-ai-output-json-schema.md.
They intentionally reject extra fields so AI output drift is caught before
callback payloads reach the Java backend.
"""

from typing import Literal, Optional
from uuid import UUID

from pydantic import BaseModel, Field, model_validator


VideoTypeEnum = Literal[
    "pain_point_solution",
    "before_after",
    "review",
    "product_showcase",
    "ugc_style",
    "tutorial",
]

TaskModeEnum = Literal[
    "PRODUCT_CREATIVE",
    "REFERENCE_STORYBOARD",
    "USER_SCRIPT",
    "CUSTOM_STORYBOARD",
]

VideoTaskStatusEnum = Literal[
    "draft",
    "asset_uploading",
    "asset_analyzing",
    "waiting_asset_confirmation",
    "reference_analyzing",
    "plan_generating",
    "analyzing",
    "analysis_completed",
    "plan_generated",
    "waiting_plan_selection",
    "storyboard_generating",
    "script_generating",
    "script_generated",
    "material_generating",
    "material_generated",
    "rendering",
    "checking",
    "completed",
    "failed",
    "exported",
    "waiting_storyboard_confirmation",
    "keyframe_configuring",
    "image_generating",
    "waiting_image_confirmation",
    "video_clip_generating",
    "waiting_video_clip_confirmation",
    "waiting_final_review",
    "repairing",
    "cancelled",
]

NextTaskStatusEnum = VideoTaskStatusEnum

MaterialTypeEnum = Literal[
    "product_image",
    "product_image_motion",
    "ai_image",
    "ai_video",
    "text_animation",
    "uploaded_video",
]

MaterialAssetTypeEnum = Literal[
    "image",
    "video",
    "product_image",
    "cover_image",
    "audio",
    "subtitle",
]

AssetKindEnum = Literal["image", "video", "audio", "text", "other"]
AssetRoleEnum = Literal[
    "product_front",
    "product_back",
    "product_detail",
    "model_reference",
    "scene_reference",
    "outfit_reference",
    "reference_video",
    "user_keyframe",
    "generated_result",
    "ai_keyframe",
    "image_variant",
    "video_clip",
    "final_video",
    "cover_image",
]
AssetSourceEnum = Literal["user_upload", "ai_generated", "external_url", "system"]

GeneratedItemStatusEnum = Literal["completed", "failed"]
CreativeItemStatusEnum = Literal[
    "draft",
    "generating",
    "generated",
    "uploaded",
    "confirmed",
    "rejected",
    "failed",
]
KeyframeSourceEnum = Literal["user_upload", "existing_asset", "ai_generated"]
VideoClipSourceEnum = Literal["user_upload", "ai_generated"]
ImagePurposeEnum = Literal["first_frame", "last_frame", "reference", "product_detail"]

CallbackStageEnum = Literal[
    "asset_analysis",
    "reference_analysis",
    "creative_plan",
    "product_analysis",
    "video_plan",
    "storyboard",
    "material",
    "quality_check",
    "render_manifest",
    "keyframe",
    "video_clip",
    "qa",
    "repair",
]

CallbackStatusEnum = Literal["success", "failed"]


class StrictModel(BaseModel):
    model_config = {"extra": "forbid"}


class ProductAnalysis(StrictModel):
    """Legacy product analysis result."""

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


class VideoPlanItem(StrictModel):
    """Single video plan."""

    type: VideoTypeEnum
    title: str = Field(min_length=1)
    hook: str = Field(min_length=1, max_length=120)
    structure: str = Field(min_length=1)
    reason: str = Field(min_length=1)
    estimatedDuration: int = Field(ge=15, le=30)
    score: int = Field(ge=0, le=100)


class VideoPlanResult(StrictModel):
    """Three to five video plans."""

    plans: list[VideoPlanItem] = Field(min_length=3, max_length=5)


class ShotItem(StrictModel):
    """Single storyboard shot."""

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


class StoryboardResult(StrictModel):
    """Storyboard result."""

    title: str = Field(min_length=1, max_length=120)
    hook: str = Field(min_length=1, max_length=120)
    duration: int = Field(ge=15, le=30)
    caption: str = Field(min_length=1, max_length=500)
    hashtags: list[str] = Field(min_length=1, max_length=10)
    coverText: str = Field(min_length=1, max_length=80)
    musicSuggestion: str = Field(min_length=1)
    shots: list[ShotItem] = Field(min_length=4, max_length=12)


class MaterialItem(StrictModel):
    """Single generated material."""

    shotNo: int = Field(ge=1)
    type: MaterialAssetTypeEnum
    status: GeneratedItemStatusEnum
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


class MaterialResult(StrictModel):
    """Material generation result."""

    materials: list[MaterialItem] = Field(min_length=1)


class QualityChecks(StrictModel):
    """Legacy quality check flags."""

    hasHook: bool
    productAppearsEarly: bool
    subtitleReadable: bool
    noSensitiveClaims: bool
    visualQualityAcceptable: bool


class QualityCheckResult(StrictModel):
    """Legacy quality check result."""

    qualityScore: int = Field(ge=0, le=100)
    riskScore: int = Field(ge=0, le=100)
    checks: QualityChecks
    complianceTips: list[str] = Field(default_factory=list)
    forbiddenClaims: list[str] = Field(default_factory=list)
    needsHumanReview: bool = False
    suggestions: list[str] = Field(default_factory=list)


class FashionVisualFeatures(StrictModel):
    colors: list[str] = Field(default_factory=list)
    patterns: list[str] = Field(default_factory=list)
    materials: list[str] = Field(default_factory=list)
    fit: Optional[str] = None
    occasions: list[str] = Field(default_factory=list)


class FashionAssetAnalysis(StrictModel):
    """Fashion vertical asset analysis result."""

    productCategory: str = Field(min_length=1)
    styleAttributes: list[str] = Field(min_length=1)
    visualFeatures: FashionVisualFeatures = Field(default_factory=FashionVisualFeatures)
    recommendedAngles: list[str] = Field(min_length=1)
    assetQualityScore: int = Field(ge=0, le=100)
    missingAngles: list[str] = Field(default_factory=list)
    lightingNotes: Optional[str] = None
    backgroundRecommendations: list[str] = Field(default_factory=list)
    modelRequirements: Optional[str] = None


class ReferenceShotItem(StrictModel):
    shotNo: int = Field(ge=1)
    startTime: Optional[float] = Field(default=None, ge=0)
    endTime: Optional[float] = Field(default=None, ge=0)
    duration: Optional[float] = Field(default=None, ge=0)
    scene: str = Field(min_length=1)
    action: str = Field(default="")
    camera: Optional[str] = None
    transition: Optional[str] = None
    subtitle: Optional[str] = None
    structureRole: Optional[str] = None


class ReferenceVideoAnalysis(StrictModel):
    """Reference video storyboard and structure analysis."""

    title: Optional[str] = None
    duration: Optional[float] = Field(default=None, ge=0)
    hook: Optional[str] = None
    structure: list[str] = Field(default_factory=list)
    shots: list[ReferenceShotItem] = Field(min_length=1)
    reusablePatterns: list[str] = Field(default_factory=list)
    riskTips: list[str] = Field(default_factory=list)


class CreativePlanItem(VideoPlanItem):
    taskMode: Optional[TaskModeEnum] = None
    requiredAssets: list[AssetRoleEnum] = Field(default_factory=list)
    estimatedCostTier: Optional[Literal["cheap", "moderate", "expensive"]] = None


class CreativePlanResult(StrictModel):
    """Fashion creative plans."""

    plans: list[CreativePlanItem] = Field(min_length=1, max_length=5)


class KeyframeItem(StrictModel):
    shotNo: int = Field(ge=1)
    status: GeneratedItemStatusEnum
    url: Optional[str] = None
    prompt: Optional[str] = None
    negativePrompt: Optional[str] = None
    provider: Optional[str] = None
    modelName: Optional[str] = None
    qualityScore: Optional[int] = Field(default=None, ge=0, le=100)
    source: Optional[KeyframeSourceEnum] = None
    imagePurpose: Optional[ImagePurposeEnum] = None
    errorMessage: Optional[str] = None

    @model_validator(mode="after")
    def conditional_fields(self) -> "KeyframeItem":
        if self.status == "completed" and not self.url:
            raise ValueError("url is required when status is 'completed'")
        if self.status == "failed" and not self.errorMessage:
            raise ValueError("errorMessage is required when status is 'failed'")
        return self


class KeyframeGenerationResult(StrictModel):
    keyframes: list[KeyframeItem] = Field(min_length=1)


class VideoClipItem(StrictModel):
    shotNo: int = Field(ge=1)
    status: GeneratedItemStatusEnum
    url: Optional[str] = None
    prompt: Optional[str] = None
    negativePrompt: Optional[str] = None
    provider: Optional[str] = None
    modelName: Optional[str] = None
    duration: Optional[int] = Field(default=None, ge=1, le=8)
    qualityScore: Optional[int] = Field(default=None, ge=0, le=100)
    source: Optional[VideoClipSourceEnum] = None
    errorMessage: Optional[str] = None

    @model_validator(mode="after")
    def conditional_fields(self) -> "VideoClipItem":
        if self.status == "completed":
            if not self.url:
                raise ValueError("url is required when status is 'completed'")
            if self.duration is None:
                raise ValueError("duration is required when status is 'completed'")
        if self.status == "failed" and not self.errorMessage:
            raise ValueError("errorMessage is required when status is 'failed'")
        return self


class VideoClipGenerationResult(StrictModel):
    clips: list[VideoClipItem] = Field(min_length=1)


class RepairPreserveConstraints(StrictModel):
    productDetails: list[str] = Field(default_factory=list)
    styleAttributes: list[str] = Field(default_factory=list)


class RepairResult(StrictModel):
    feedbackCategory: Literal[
        "visual_quality",
        "product_accuracy",
        "lighting_issue",
        "action_stiffness",
        "missing_detail",
        "layout_composition",
        "style_mismatch",
        "other",
    ]
    targetType: Literal["storyboard", "keyframe", "video_clip", "plan"]
    strategy: Literal[
        "rewrite_storyboard_shot",
        "regenerate_keyframe_prompt",
        "regenerate_keyframe",
        "regenerate_video_clip_prompt",
        "regenerate_video_clip",
        "adjust_edit_instruction",
        "reorder_shots",
    ]
    affectedShots: list[int] = Field(min_length=1)
    repairNotes: Optional[str] = None
    preserveConstraints: Optional[RepairPreserveConstraints] = None
    newPrompt: Optional[str] = None
    newStoryboardShot: Optional[dict] = None
    estimatedCostTier: Optional[Literal["cheap", "moderate", "expensive"]] = None
    requiresUserConfirmation: Optional[bool] = None

    @model_validator(mode="after")
    def affected_shots_positive(self) -> "RepairResult":
        if any(shot_no < 1 for shot_no in self.affectedShots):
            raise ValueError("affectedShots items must be >= 1")
        return self


class FashionQaChecks(StrictModel):
    productVisible: bool
    styleAccurate: bool
    lightingAcceptable: bool
    compositionValid: bool
    noArtifacts: bool
    modelNatural: Optional[bool] = None
    fabricDetailVisible: Optional[bool] = None
    colorAccuracy: Optional[bool] = None


class FashionQaResult(StrictModel):
    stage: Literal["keyframe", "video_clip", "storyboard", "final_video"]
    qualityScore: int = Field(ge=0, le=100)
    riskScore: Optional[int] = Field(default=None, ge=0, le=100)
    checks: FashionQaChecks
    suggestions: list[str] = Field(default_factory=list)
    complianceTips: list[str] = Field(default_factory=list)
    forbiddenClaims: list[str] = Field(default_factory=list)
    needsHumanReview: bool


class CallbackError(StrictModel):
    """Callback error payload."""

    errorCode: str
    errorMessage: str
    failedStage: str
    retryable: bool
    provider: Optional[str] = None
    rawError: Optional[dict] = None


class CallbackPayload(StrictModel):
    """AI callback payload sent to Java."""

    taskId: UUID
    schemaVersion: str = Field(default="1.0.0", pattern=r"^1\.0\.0$")
    stage: CallbackStageEnum
    status: CallbackStatusEnum
    nextTaskStatus: Optional[NextTaskStatusEnum] = None

    fashionAssetAnalysis: Optional[FashionAssetAnalysis] = None
    referenceAnalysis: Optional[ReferenceVideoAnalysis] = None
    productAnalysis: Optional[ProductAnalysis | dict] = None
    plans: Optional[list[VideoPlanItem] | list[dict]] = None
    storyboard: Optional[StoryboardResult | dict] = None
    materials: Optional[list[MaterialItem] | list[dict]] = None
    qualityCheck: Optional[QualityCheckResult | dict] = None
    renderManifest: Optional[dict] = None
    keyframes: Optional[list[KeyframeItem] | list[dict]] = None
    clips: Optional[list[VideoClipItem] | list[dict]] = None
    qaResult: Optional[FashionQaResult] = None
    repairResult: Optional[RepairResult] = None

    error: Optional[CallbackError] = None

    @model_validator(mode="after")
    def payload_matches_status_and_stage(self) -> "CallbackPayload":
        if self.status == "failed":
            if self.error is None:
                raise ValueError("error is required when status is 'failed'")
            return self

        stage_payload_fields = {
            "asset_analysis": "fashionAssetAnalysis",
            "reference_analysis": "referenceAnalysis",
            "creative_plan": "plans",
            "product_analysis": "productAnalysis",
            "video_plan": "plans",
            "storyboard": "storyboard",
            "material": "materials",
            "quality_check": "qualityCheck",
            "render_manifest": "renderManifest",
            "keyframe": "keyframes",
            "video_clip": "clips",
            "qa": "qaResult",
            "repair": "repairResult",
        }
        field_name = stage_payload_fields[self.stage]
        if getattr(self, field_name) is None:
            raise ValueError(f"{field_name} is required when stage is '{self.stage}'")
        return self
