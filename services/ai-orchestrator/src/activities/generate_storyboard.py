"""Activity: Generate storyboard with 4-12 shots."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.services.validation_pipeline import validate_and_repair
from src.schemas.ai_outputs import StoryboardResult


@activity.defn
async def generate_storyboard(script: dict, plan: dict, product_context: dict, duration: int, video_type: str) -> dict:
    system_prompt = (
        "You are a video storyboard artist for TikTok. Create a storyboard. "
        "Fields: title (max 120), hook (max 120), duration (15-30), caption (max 500), "
        "hashtags (1-10, each starting with #), coverText (max 80), musicSuggestion, "
        "shots (4-12). Each shot: shotNo, duration (1-8), scene, action, subtitle (max 90), "
        f"materialType ({'product_image/product_image_motion/ai_image/ai_video/text_animation/uploaded_video'}), "
        "prompt (required for ai_image/ai_video), negativePrompt, editInstruction. "
        "Strict JSON only."
    )
    user_prompt = f"Script:\n{script}\n\nPlan:\n{plan}\n\nProduct:\n{product_context}\n\nTarget duration: {duration}s, type: {video_type}"

    try:
        result = validate_and_repair(
            await call_llm("storyboard", system_prompt, user_prompt),
            StoryboardResult,
        )
    except ValueError as e:
        activity.logger.error("StoryboardResult validation failed: %s", e)
        raise

    return result.model_dump()
