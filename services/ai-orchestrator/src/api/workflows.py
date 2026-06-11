"""Workflow trigger endpoints.

V1 提供两个端点：
- POST /ai/workflows/product-analysis  — Java 调用，启动商品分析 + 方案生成
- POST /ai/workflows/selected-plan-generation — Java 调用，启动脚本/分镜/素材/RenderManifest 生成
"""

from fastapi import APIRouter, HTTPException

from src.schemas.workflow_requests import (
    ProductAnalysisRequest,
    SelectedPlanGenerationRequest,
    WorkflowTriggerResponse,
)
from src.config import settings

router = APIRouter()


@router.post("/workflows/product-analysis", response_model=WorkflowTriggerResponse)
async def start_product_analysis(req: ProductAnalysisRequest):
    """
    Java 调用：启动 ProductAnalysisWorkflow。
    包含 analyze_product → generate_video_plans → callback_java
    """
    # TODO: Phase 4 — start Temporal workflow
    return WorkflowTriggerResponse(
        workflow_id=f"wf-pa-{req.taskId[:8]}",
        status="accepted",
        message="ProductAnalysisWorkflow will be started (Phase 4)",
    )


@router.post("/workflows/selected-plan-generation", response_model=WorkflowTriggerResponse)
async def start_selected_plan_generation(req: SelectedPlanGenerationRequest):
    """
    Java 调用：启动 SelectedPlanGenerationWorkflow。
    包含 generate_script → generate_storyboard → ... → build_render_manifest → callback_java
    """
    # TODO: Phase 4 — start Temporal workflow
    return WorkflowTriggerResponse(
        workflow_id=f"wf-spg-{req.taskId[:8]}",
        status="accepted",
        message="SelectedPlanGenerationWorkflow will be started (Phase 4)",
    )
