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
    selectedPlan: dict
    duration: int = Field(ge=15, le=30)
    videoType: str
    needSubtitles: bool = True
    needVoiceover: bool = False


class WorkflowTriggerResponse(BaseModel):
    workflow_id: str
    status: str
    message: str
