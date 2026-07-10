"""Temporal Workflows for AI Video Orchestration."""

from .product_analysis import ProductAnalysisWorkflow
from .selected_plan_generation import SelectedPlanGenerationWorkflow
from .fashion_analysis import FashionAnalysisWorkflow
from .fashion_plan import FashionPlanWorkflow
from .fashion_storyboard import FashionStoryboardWorkflow
from .fashion_keyframe import FashionKeyframeWorkflow
from .fashion_video_clip import FashionVideoClipWorkflow
from .fashion_repair import FashionRepairWorkflow

ALL_WORKFLOWS = [
    ProductAnalysisWorkflow,
    SelectedPlanGenerationWorkflow,
    FashionAnalysisWorkflow,
    FashionPlanWorkflow,
    FashionStoryboardWorkflow,
    FashionKeyframeWorkflow,
    FashionVideoClipWorkflow,
    FashionRepairWorkflow,
]
