"""Workflow trigger endpoints — called by Java AiServiceClient."""

import logging
from fastapi import APIRouter, HTTPException, Request

from src.schemas.workflow_requests import (
    ProductAnalysisRequest,
    SelectedPlanGenerationRequest,
    WorkflowTriggerResponse,
)

log = logging.getLogger(__name__)
router = APIRouter()


@router.post("/workflows/product-analysis", response_model=WorkflowTriggerResponse)
async def start_product_analysis(req: ProductAnalysisRequest, request: Request):
    """Start ProductAnalysisWorkflow: analyze_product → generate_video_plans → callback_java."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"pa-{req.taskId}"
    await client.start_workflow(
        "ProductAnalysisWorkflow",
        args=[str(req.taskId), req.productContext],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started ProductAnalysisWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="ProductAnalysisWorkflow started",
    )


@router.post("/workflows/selected-plan-generation", response_model=WorkflowTriggerResponse)
async def start_selected_plan_generation(req: SelectedPlanGenerationRequest, request: Request):
    """Start SelectedPlanGenerationWorkflow: script → storyboard → materials → render_manifest → callback."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"spg-{req.taskId}"
    await client.start_workflow(
        "SelectedPlanGenerationWorkflow",
        args=[str(req.taskId), req.productContext, req.selectedPlan, req.duration, req.videoType],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started SelectedPlanGenerationWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="SelectedPlanGenerationWorkflow started",
    )
