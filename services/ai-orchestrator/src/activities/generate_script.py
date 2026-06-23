"""Activity: Generate video script."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.schemas.ai_outputs import VideoPlanItem


@activity.defn
async def generate_script(plan: dict, product_context: dict) -> dict:
    system_prompt = (
        "You are a TikTok video scriptwriter. Write a script based on the plan. "
        "Return JSON: title, hook, script (full script text), caption, hashtags (array). "
        "Strict JSON only."
    )
    user_prompt = f"Plan:\n{plan}\n\nProduct:\n{product_context}"

    raw = await call_llm("script", system_prompt, user_prompt)
    return raw if isinstance(raw, dict) else {"script": str(raw)}
