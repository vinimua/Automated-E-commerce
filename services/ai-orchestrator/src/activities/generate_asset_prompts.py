"""Activity: Generate detailed image/video prompts for each shot."""

from temporalio import activity


@activity.defn
async def generate_asset_prompts(storyboard: dict) -> dict:
    """Pass-through: the storyboard already contains prompts for ai_image/ai_video shots."""
    return storyboard
