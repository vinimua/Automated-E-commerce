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
from .analyze_fashion_assets import analyze_fashion_assets
from .analyze_reference_video import analyze_reference_video
from .generate_fashion_plans import generate_fashion_plans
from .generate_fashion_storyboard import generate_fashion_storyboard
from .generate_keyframe_prompts import generate_keyframe_prompts
from .fake_generate_keyframes import fake_generate_keyframes
from .generate_video_clip_prompts import generate_video_clip_prompts
from .fake_generate_video_clips import fake_generate_video_clips
from .classify_feedback import classify_feedback
from .plan_repair import plan_repair

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
    analyze_fashion_assets,
    analyze_reference_video,
    generate_fashion_plans,
    generate_fashion_storyboard,
    generate_keyframe_prompts,
    fake_generate_keyframes,
    generate_video_clip_prompts,
    fake_generate_video_clips,
    classify_feedback,
    plan_repair,
]
