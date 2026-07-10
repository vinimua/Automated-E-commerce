"""Workflow trigger endpoints — called by Java AiServiceClient."""

import logging
from fastapi import APIRouter, HTTPException, Request

from src.schemas.workflow_requests import (
    ProductAnalysisRequest,
    SelectedPlanGenerationRequest,
    AssetAnalysisRequest,
    ReferenceAnalysisRequest,
    CreativePlanRequest,
    StoryboardGenerationRequest,
    KeyframeGenerationRequest,
    VideoClipGenerationRequest,
    RepairRequest,
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


# ── Fashion Creative Loop V1 endpoints ──


@router.post("/workflows/asset-analysis", response_model=WorkflowTriggerResponse)
async def start_asset_analysis(req: AssetAnalysisRequest, request: Request):
    """Start FashionAnalysisWorkflow for product asset analysis."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"fa-{req.taskId}"
    await client.start_workflow(
        "FashionAnalysisWorkflow",
        args=[str(req.taskId), req.productContext, "asset", ""],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionAnalysisWorkflow (asset): workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionAnalysisWorkflow (asset) started",
    )


@router.post("/workflows/reference-analysis", response_model=WorkflowTriggerResponse)
async def start_reference_analysis(req: ReferenceAnalysisRequest, request: Request):
    """Start FashionAnalysisWorkflow for reference video analysis."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"ref-{req.taskId}"
    await client.start_workflow(
        "FashionAnalysisWorkflow",
        args=[str(req.taskId), req.productContext, "reference", req.referenceUrl],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionAnalysisWorkflow (reference): workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionAnalysisWorkflow (reference) started",
    )


@router.post("/workflows/creative-plan-generation", response_model=WorkflowTriggerResponse)
async def start_creative_plan(req: CreativePlanRequest, request: Request):
    """Start FashionPlanWorkflow: generate creative plans → callback."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"fplan-{req.taskId}"
    await client.start_workflow(
        "FashionPlanWorkflow",
        args=[str(req.taskId), req.productContext, req.assetAnalysis or {}],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionPlanWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionPlanWorkflow started",
    )


@router.post("/workflows/storyboard-generation", response_model=WorkflowTriggerResponse)
async def start_storyboard_generation(req: StoryboardGenerationRequest, request: Request):
    """Start FashionStoryboardWorkflow: generate storyboard → callback."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"fsb-{req.taskId}"
    await client.start_workflow(
        "FashionStoryboardWorkflow",
        args=[str(req.taskId), req.productContext, req.selectedPlan, req.duration, req.videoType],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionStoryboardWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionStoryboardWorkflow started",
    )


@router.post("/workflows/keyframe-generation", response_model=WorkflowTriggerResponse)
async def start_keyframe_generation(req: KeyframeGenerationRequest, request: Request):
    """Start FashionKeyframeWorkflow: prompts → fake keyframes → callback."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"fkf-{req.taskId}"
    await client.start_workflow(
        "FashionKeyframeWorkflow",
        args=[str(req.taskId), req.storyboard],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionKeyframeWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionKeyframeWorkflow started",
    )


@router.post("/workflows/video-clip-generation", response_model=WorkflowTriggerResponse)
async def start_video_clip_generation(req: VideoClipGenerationRequest, request: Request):
    """Start FashionVideoClipWorkflow: prompts → fake clips → callback."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"fvc-{req.taskId}"
    await client.start_workflow(
        "FashionVideoClipWorkflow",
        args=[str(req.taskId), req.storyboard, req.keyframes],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionVideoClipWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionVideoClipWorkflow started",
    )


@router.post("/workflows/repair", response_model=WorkflowTriggerResponse)
async def start_repair(req: RepairRequest, request: Request):
    """Start FashionRepairWorkflow: classify feedback → plan repair → callback."""
    client = getattr(request.app.state, "temporal_client", None)
    if client is None:
        raise HTTPException(status_code=503, detail="Temporal service unavailable")

    workflow_id = f"frep-{req.taskId}"
    await client.start_workflow(
        "FashionRepairWorkflow",
        args=[str(req.taskId), req.feedbackText, req.category, req.targetType, req.currentState],
        id=workflow_id,
        task_queue="ai-video-task-queue",
    )
    log.info("Started FashionRepairWorkflow: workflow_id=%s, taskId=%s", workflow_id, req.taskId)

    return WorkflowTriggerResponse(
        workflow_id=workflow_id,
        status="started",
        message="FashionRepairWorkflow started",
    )
