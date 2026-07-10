"""Activity: Analyze fashion product assets — style, visual features, missing angles."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import FashionAssetAnalysis


@activity.defn
async def analyze_fashion_assets(task_id: str, asset_context: dict) -> dict:
    """Analyze fashion product assets and return structured FashionAssetAnalysis.

    In fake mode, returns fixture data matching FashionAssetAnalysis schema.
    When TEXT_LLM_API_KEY is configured, uses the LLM for real analysis.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("fashion_asset_analysis"):
        result = get_fashion_fixture("fashion_asset_analysis")
    else:
        from src.services.llm_service import call_llm
        from src.services.validation_pipeline import validate_and_repair

        system_prompt = (
            "You are a fashion e-commerce asset analyst. Analyze the product images and return JSON. "
            "Fields: productCategory (string), styleAttributes (array, min 1), "
            "visualFeatures (object with colors, patterns, materials arrays, fit string, occasions array), "
            "recommendedAngles (array, min 1), assetQualityScore (0-100), "
            "missingAngles (array), lightingNotes (string), backgroundRecommendations (array), "
            "modelRequirements (string). "
            "Strict JSON only, no markdown, no extra fields."
        )
        user_prompt = f"Analyze these fashion product assets:\n{asset_context}"
        result = validate_and_repair(
            await call_llm("fashion_asset_analysis", system_prompt, user_prompt),
            FashionAssetAnalysis,
        ).model_dump()

    activity.logger.info("Fashion asset analysis complete: category=%s, score=%d",
                         result.get("productCategory"), result.get("assetQualityScore", 0))
    return result
