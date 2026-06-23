"""Temporal Activities for AI Video Orchestration."""

from .analyze_product import analyze_product
from .generate_video_plans import generate_video_plans
from .generate_script import generate_script
from .generate_storyboard import generate_storyboard
from .generate_asset_prompts import generate_asset_prompts
from .generate_image_assets import generate_image_assets
from .generate_video_clips import generate_video_clips
from .check_asset_quality import check_asset_quality
from .build_render_manifest import build_render_manifest
from .callback_java import callback_java

ALL_ACTIVITIES = [
    analyze_product,
    generate_video_plans,
    generate_script,
    generate_storyboard,
    generate_asset_prompts,
    generate_image_assets,
    generate_video_clips,
    check_asset_quality,
    build_render_manifest,
    callback_java,
]
