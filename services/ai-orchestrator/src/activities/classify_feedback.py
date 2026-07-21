"""Activity: Classify user feedback into structured repair strategy using LLM."""

from temporalio import activity
from src.config import settings
from src.schemas.ai_outputs import RepairResult


CLASSIFY_SYSTEM_PROMPT = """你是视频创作修复分类专家。根据用户反馈，输出结构化的修复分类。

字段说明：
- feedbackCategory: visual_quality（画质模糊/不清晰）、product_accuracy（商品颜色/图案/版型错误）、lighting_issue（光线/曝光问题）、action_stiffness（动作僵硬不自然）、missing_detail（缺少关键细节）、layout_composition（构图/布局问题）、style_mismatch（风格不符）、other（其他）
- targetType: storyboard（分镜脚本）、keyframe（关键帧图片）、video_clip（视频片段）、plan（创意方案）、render_manifest（渲染清单）、final_video（最终成片）
- strategy: rewrite_storyboard_shot（重写分镜）、regenerate_keyframe_prompt（重写关键帧提示词）、regenerate_keyframe（重新生成关键帧）、regenerate_video_clip_prompt（重写视频提示词）、regenerate_video_clip（重新生成视频）、adjust_edit_instruction（调整剪辑指令）、reorder_shots（调整镜头顺序）
- affectedShots: 受影响的镜头编号数组
- repairNotes: 中文修复说明（50-200字）
- estimatedCostTier: cheap（单次重生成）、moderate（多次重生成）、expensive（需重新分镜）
- newPrompt: 仅在需要重写提示词时输出，英文提示词

只返回严格 JSON。不要 Markdown。所有文本字段（feedbackCategory除外）使用中文。"""


@activity.defn
async def classify_feedback(
    task_id: str,
    feedback_text: str,
    category: str,
    target_type: str,
) -> dict:
    """Classify user feedback into a RepairResult with strategy and affected shots."""
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("feedback_classification"):
        from src.services.llm_service import get_fashion_fixture
        result = get_fashion_fixture("feedback_classification")
        result["repairNotes"] = f"User feedback: {feedback_text}"
        activity.logger.info("Feedback classified (fake): category=%s", result.get("feedbackCategory"))
        RepairResult.model_validate(result)
        return result

    from src.services.llm_service import call_llm
    from src.services.validation_pipeline import validate_and_repair

    user_prompt = (
        f"用户反馈：{feedback_text}\n"
        f"用户选择的问题类型：{category}\n"
        f"修复目标类型：{target_type}\n\n"
        "请根据反馈内容判断真实的 feedbackCategory、strategy 和 affectedShots。"
    )
    result = validate_and_repair(
        await call_llm("feedback_classification", CLASSIFY_SYSTEM_PROMPT, user_prompt),
        RepairResult,
    ).model_dump()
    activity.logger.info("Feedback classified: category=%s, strategy=%s, shots=%s",
                         result.get("feedbackCategory"), result.get("strategy"),
                         result.get("affectedShots"))
    return result
