"""Temporal Workflows for AI Video Orchestration."""

from .product_analysis import ProductAnalysisWorkflow
from .selected_plan_generation import SelectedPlanGenerationWorkflow

ALL_WORKFLOWS = [
    ProductAnalysisWorkflow,
    SelectedPlanGenerationWorkflow,
]
