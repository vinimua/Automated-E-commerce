"""Activity: Analyze reference video — extract storyboard structure, shots, patterns."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import ReferenceVideoAnalysis


@activity.defn
async def analyze_reference_video(task_id: str, reference_url: str) -> dict:
    """Analyze a reference video and extract its storyboard structure.

    In fake mode, returns fixture data matching ReferenceVideoAnalysis schema.
    When TEXT_LLM_API_KEY is configured, uses the LLM for real analysis.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("reference_video_analysis"):
        result = get_fashion_fixture("reference_video_analysis")
    else:
        from src.services.llm_service import call_llm
        from src.services.validation_pipeline import validate_and_repair

        system_prompt = (
            "You are a video structure analyst. Analyze the reference video and extract its storyboard. "
            "Fields: title (string), duration (float), hook (string), structure (array of strings), "
            "shots (array, min 1) each with: shotNo (int >=1), startTime (float), endTime (float), "
            "duration (float), scene (string), action (string), camera (string optional), "
            "transition (string optional), subtitle (string optional), structureRole (string optional), "
            "reusablePatterns (array), riskTips (array). "
            "Strict JSON only, no markdown, no extra fields."
        )
        user_prompt = f"Analyze this reference video: {reference_url}"
        result = validate_and_repair(
            await call_llm("reference_video_analysis", system_prompt, user_prompt),
            ReferenceVideoAnalysis,
        ).model_dump()

    activity.logger.info("Reference video analysis complete: title=%s, shots=%d",
                         result.get("title"), len(result.get("shots", [])))
    return result
