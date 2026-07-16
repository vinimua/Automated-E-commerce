"""Pydantic models for workflow trigger requests and responses."""

from pydantic import BaseModel, Field
from uuid import UUID
from typing import Optional


class ProductAnalysisRequest(BaseModel):
    """Request from Java to start ProductAnalysisWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    productContext: dict = Field(
        description="Product info: name, description, images, targetMarket, language"
    )


class SelectedPlanGenerationRequest(BaseModel):
    """Request from Java to start SelectedPlanGenerationWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    selectedPlanId: UUID
    productContext: dict = Field(default_factory=dict)
    selectedPlan: dict
    duration: int = Field(ge=15, le=30)
    videoType: str
    needSubtitles: bool = True
    needVoiceover: bool = False


class AssetAnalysisRequest(BaseModel):
    """Request from Java to start FashionAnalysisWorkflow (asset analysis)."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    productContext: dict = Field(default_factory=dict)
    assets: list[dict] = Field(default_factory=list, description="Task assets to analyze")


class ReferenceAnalysisRequest(BaseModel):
    """Request from Java to start FashionAnalysisWorkflow (reference video analysis)."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    referenceUrl: str = Field(min_length=1)
    productContext: dict = Field(default_factory=dict)


class CreativePlanRequest(BaseModel):
    """Request from Java to start FashionPlanWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    creativeContext: dict = Field(default_factory=dict)


class StoryboardGenerationRequest(BaseModel):
    """Request from Java to start FashionStoryboardWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    creativeContext: dict = Field(default_factory=dict)
    selectedPlan: dict
    duration: int = Field(ge=15, le=30)
    videoType: str


class KeyframeGenerationRequest(BaseModel):
    """Request from Java to start FashionKeyframeWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    storyboard: dict


class VideoClipGenerationRequest(BaseModel):
    """Request from Java to start FashionVideoClipWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    storyboard: dict
    keyframes: dict = Field(default_factory=lambda: {"keyframes": []})


class RepairRequest(BaseModel):
    """Request from Java to start FashionRepairWorkflow."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    repairEventId: UUID | None = None
    feedbackText: str = Field(min_length=1)
    category: str = Field(min_length=1)
    targetType: str = Field(min_length=1)
    currentState: dict = Field(default_factory=dict)


class AssetImageGenerationRequest(BaseModel):
    """Synchronous request from Java to generate or edit a task-level product image."""

    taskId: UUID
    productId: UUID
    userId: UUID
    correlationId: str
    prompt: str = Field(min_length=1)
    productContext: dict = Field(default_factory=dict)
    sourceAssets: list[dict] = Field(default_factory=list)
    assetRole: str = "image_variant"
    feedback: str | None = None
    previousPrompt: str | None = None
    previousResult: dict | None = None


class AssetImageGenerationResponse(BaseModel):
    url: str
    provider: str
    model: str
    prompt: str
    negativePrompt: str = ""
    qualityScore: Optional[int] = None


class WorkflowTriggerResponse(BaseModel):
    workflow_id: str
    status: str
    message: str
