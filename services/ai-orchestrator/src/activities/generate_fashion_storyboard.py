"""Activity: Generate fashion storyboard — shot-by-shot breakdown."""

from temporalio import activity
from src.services.llm_service import get_fashion_fixture
from src.schemas.ai_outputs import StoryboardResult


@activity.defn
async def generate_fashion_storyboard(
    task_id: str,
    plan: dict,
    creative_context: dict,
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
            "你是时尚视频分镜师。请按镜头逐一创建分镜脚本。"
            "字段说明：title（中文标题，1-200字）、hook（中文钩子，1-200字）、duration（整数15-30秒）、"
            "caption（中文视频文案，1-500字）、hashtags（1-10个中文标签数组，格式 #中文标签）、"
            "coverText（封面中文文案，1-80字）、musicSuggestion（中文音乐建议）、"
            "shots（4-8个镜头数组）：shotNo（整数>=1）、duration（1-8整数秒）、"
            "scene（中文场景描述）、action（中文动作描述）、subtitle（中文字幕文案，1-90字）、"
            "materialType（product_image/product_image_motion/ai_image/ai_video/text_animation/uploaded_video）、"
            "prompt（仅当 materialType 为 ai_image 或 ai_video 时必填，用英文写图像/视频生成提示词）、"
            "negativePrompt（英文负面提示词）、editInstruction（中文剪辑指令）。"
            f"目标总时长：{duration}秒，视频类型：{video_type}。"
            "所有面向用户的文本字段（title、hook、caption、hashtags、coverText、musicSuggestion、scene、action、subtitle、editInstruction）必须使用中文。"
            "仅 prompt 和 negativePrompt 使用英文（因为图像/视频生成模型对英文更友好）。"
            "只返回严格 JSON，不要 Markdown，不要额外字段。"
        )
        user_prompt = (
            f"已选方案：{plan}\n\n完整创意上下文：{creative_context}\n\n"
            "请使用 assetAnalysis.analysisText 判断哪些内容可以被展示。"
            "严格遵循 userRequest 的创意意图、productProfile 的商品事实，"
            "以及 analysisText 中列出的所有限制和避坑指令。所有文案使用中文。"
        )
        result = validate_and_repair(
            await call_llm("fashion_storyboard", system_prompt, user_prompt),
            StoryboardResult,
        ).model_dump()

    activity.logger.info("Fashion storyboard generated: shots=%d", len(result.get("shots", [])))
    return result
