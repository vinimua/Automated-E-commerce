"""Activity: Generate fashion storyboard — shot-by-shot breakdown."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import StoryboardResult


@activity.defn
async def generate_fashion_storyboard(
    task_id: str,
    plan: dict,
    product_context: dict,
    duration: int,
    video_type: str,
) -> dict:
    """Generate a detailed storyboard with 4-8 shots for the fashion video.

    In fake mode, returns fixture data matching StoryboardResult schema.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("fashion_storyboard"):
        result = get_fashion_fixture("fashion_storyboard")
    else:
        from src.services.llm_service import call_llm
        from src.services.validation_pipeline import validate_and_repair

        system_prompt = (
            "You are a fashion video storyboard artist. Create a shot-by-shot storyboard. "
            "Fields: title (1-120 chars), hook (1-120 chars), duration (15-30 int), "
            "caption (1-500 chars), hashtags (1-10 array, pattern ^#[A-Za-z0-9_]+$), "
            "coverText (1-80 chars), musicSuggestion (string), "
            "shots (4-8 array): shotNo (int >=1), duration (1-8 int), scene (string), "
            "action (string), subtitle (1-90 chars), "
            "materialType (product_image/product_image_motion/ai_image/ai_video/text_animation/uploaded_video), "
            "prompt (required when materialType is ai_image or ai_video), "
            "negativePrompt (string), editInstruction (string). "
            f"Target duration: {duration}s, Video type: {video_type}. "
            "Strict JSON only, no markdown, no extra fields."
        )
        user_prompt = f"Plan: {plan}\n\nProduct: {product_context}"
        result = validate_and_repair(
            await call_llm("fashion_storyboard", system_prompt, user_prompt),
            StoryboardResult,
        ).model_dump()

    activity.logger.info("Fashion storyboard generated: shots=%d", len(result.get("shots", [])))
    return result
