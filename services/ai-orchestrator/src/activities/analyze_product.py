"""Activity: Analyze product using multimodal LLM."""

from temporalio import activity
from src.services.llm_service import call_llm
from src.services.validation_pipeline import validate_and_repair
from src.schemas.ai_outputs import ProductAnalysis


@activity.defn
async def analyze_product(product_context: dict) -> dict:
    system_prompt = (
        "You are an e-commerce product analyst. Analyze the product and return JSON. "
        "Fields: category, sellingPoints (array, min 1), painPoints (array, min 1), "
        "targetAudience (array, min 1), scenes (array, min 1), "
        "recommendedVideoTypes (array of: pain_point_solution/before_after/review/"
        "product_showcase/ugc_style/tutorial), videoScore (0-100), riskTips (array), "
        "claimRiskLevel (low/medium/high), forbiddenClaims (array), "
        "complianceTips (array), needsHumanReview (boolean). "
        "Strict JSON only, no markdown, no extra fields."
    )
    user_prompt = f"Analyze this product:\n{product_context}"

    try:
        result = validate_and_repair(
            await call_llm("product_analysis", system_prompt, user_prompt),
            ProductAnalysis,
        )
    except ValueError as e:
        activity.logger.error("ProductAnalysis validation failed: %s", e)
        raise

    return result.model_dump()
