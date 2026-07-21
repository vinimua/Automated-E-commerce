"""Activity: Plan repair actions based on classified feedback using LLM."""

from temporalio import activity
from src.config import settings
from src.schemas.ai_outputs import RepairResult


REPAIR_SYSTEM_PROMPT = """你是视频创作修复执行专家。根据已分类的反馈，输出具体可执行的修复方案。

字段说明：
- feedbackCategory: 与输入保持一致
- targetType: 与输入保持一致
- strategy: 与输入保持一致
- affectedShots: 受影响的镜头编号数组
- repairNotes: 中文修复执行说明（100-300字），描述具体的修复步骤和预期效果
- preserveConstraints: 修复过程中必须保留的商品/风格约束
  - productDetails: 必须保持的商品细节数组
  - styleAttributes: 必须保持的风格属性数组
- estimatedCostTier: cheap（单次重生成）、moderate（多次重生成）、expensive（需重新分镜）
- newPrompt: 当 strategy 涉及重写提示词时，输出优化后的英文提示词。必须考虑：
  1. 原始提示词的优点保留
  2. 用户反馈指出的问题修复
  3. 更清晰的视觉描述
- newEditInstruction: 当 strategy 涉及调整剪辑时，输出中文优化后的剪辑指令

只返回严格 JSON。不要 Markdown。newPrompt 使用英文，其他文本字段使用中文。"""


@activity.defn
async def plan_repair(task_id: str, classification: dict, current_state: dict) -> dict:
    """Create a concrete repair plan based on classified feedback.

    Uses LLM (deepseek-v4-pro) to generate actionable repair plans with
    optimized prompts and constraints.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("repair_plan"):
        from src.services.llm_service import get_fashion_fixture
        result = get_fashion_fixture("repair_plan")
        result["feedbackCategory"] = classification.get("feedbackCategory", result["feedbackCategory"])
        result["targetType"] = classification.get("targetType", result["targetType"])
        result["strategy"] = classification.get("strategy", result["strategy"])
        result["affectedShots"] = classification.get("affectedShots", result["affectedShots"])
        activity.logger.info("Repair planned (fake): strategy=%s", result.get("strategy"))
        RepairResult.model_validate(result)
        return result

    from src.services.llm_service import call_llm
    from src.services.validation_pipeline import validate_and_repair

    user_prompt = (
        f"反馈分类结果：{classification}\n\n"
        f"当前任务状态：{current_state}\n\n"
        "请生成具体的修复执行方案。如果需要重写提示词（newPrompt），"
        "请确保新提示词修复了用户反馈的问题，同时保留原有的创意方向。"
        "所有中文文本正常输出。"
    )
    result = validate_and_repair(
        await call_llm("repair_plan", REPAIR_SYSTEM_PROMPT, user_prompt),
        RepairResult,
    ).model_dump()
    activity.logger.info("Repair planned: strategy=%s, shots=%s, cost=%s",
                         result.get("strategy"), result.get("affectedShots"),
                         result.get("estimatedCostTier"))
    return result
