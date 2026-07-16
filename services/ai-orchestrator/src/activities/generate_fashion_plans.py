"""Activity: Generate fashion creative plans based on asset analysis."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import CreativePlanResult


@activity.defn
async def generate_fashion_plans(task_id: str, creative_context: dict) -> dict:
    """Generate 3-5 fashion creative plans based on asset analysis.

    In fake mode, returns fixture data matching CreativePlanResult schema.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("fashion_plans"):
        result = get_fashion_fixture("fashion_plans")
    else:
        from src.services.llm_service import call_llm
        from src.services.validation_pipeline import validate_and_repair

        system_prompt = (
            "你是 TikTok 时尚带货视频创意策略师。请生成 3-5 个视频方案。"
            "不要从固定模板出发。每个方案必须在目标受众角度、钩子、素材使用方式、节奏和证明方式上有本质差异。"
            "方案类型只是内部策略/渲染提示，不是创意结构本身。"
            "每个方案包含：type（pain_point_solution/before_after/review/product_showcase/ugc_style/tutorial）、"
            "title（中文标题）、hook（中文钩子，最长200字）、structure（中文结构描述）、reason（中文推荐理由）、"
            "estimatedDuration（15-30整数）、score（0-100整数）、"
            "taskMode（PRODUCT_CREATIVE/REFERENCE_STORYBOARD/USER_SCRIPT/CUSTOM_STORYBOARD）、"
            "requiredAssets（数组，只能使用以下值：product_front、product_back、"
            "product_detail、model_reference、scene_reference、outfit_reference、reference_video、"
            "user_keyframe、generated_result、ai_keyframe、image_variant、video_clip、final_video、cover_image）、"
            "estimatedCostTier（cheap/moderate/expensive）。"
            "所有面向用户的文本字段（title、hook、structure、reason）必须使用中文输出。"
            "只返回 JSON：{\"plans\": [...]}。不要输出其他字段，不要 Markdown。"
        )
        user_prompt = (
            "请使用以下完整创意上下文创建方案。userRequest 为最高优先级创意意图，"
            "productProfile 为商品事实，assetAnalysis.analysisText 为视觉证据和素材使用约束。"
            f"不得编造不存在的商品功效。所有文案必须中文输出。\n\n{creative_context}"
        )
        result = validate_and_repair(
            await call_llm("fashion_plans", system_prompt, user_prompt),
            CreativePlanResult,
        ).model_dump()

    activity.logger.info("Fashion plans generated: count=%d", len(result.get("plans", [])))
    return result
