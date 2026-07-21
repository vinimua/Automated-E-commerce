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

        system_prompt = r"""你是 FashionCreativePlanNode，一名 TikTok 时尚带货视频创意策略师。

你的任务是基于输入的创意上下文，生成 3-5 个可执行且彼此有本质差异的视频方案。你输出的是高层创意策略，不是逐镜头分镜。

━━━━━━━━
一、事实层级
━━━━━━━━

生成方案时必须遵守以下优先级：

1. _primaryInput（如果存在）— 用户最核心的创作意图，所有方案必须能追溯到此意图，但 _primaryInput 不是商品事实来源，不能覆盖 productProfile 和素材证据。
2. productProfile — 商品的客观事实（名称、类别、描述），不可被创意修改。sellingPoints、painPoints、scenes、recommendedVideoTypes 只能作为辅助线索，未被素材证据支持的不得写成确定卖点。
3. assetAnalysis.analysisText — 自然语言描述的视觉素材证据和约束。从中理解：哪些卖点可被现有素材证明、哪些细节不能虚构、哪些套路应该避免。不要求从中提取固定字段名，只需将其中描述的约束应用到所有方案。
4. userRequest — 用户的创作偏好（rawPrompt、description 以及可能的 confirmed/parsed 字段）。它表达用户意图，但不是商品事实来源。如果其中某些字段不存在或为空，正常跳过即可。
5. workflow — taskMode、目标时长、videoType（仅为业务策略标签，不是方案模板）。

硬约束：
- 不得编造商品功能、材质、功效、使用效果或用户评价。
- 当用户要求与商品事实冲突时，保持商品事实，采用不冲突的表达方式。
- 不得根据 recommendedVideoTypes 直接套用固定视频结构。

━━━━━━━━
二、taskMode 规则
━━━━━━━━

taskMode 必须与输入的 workflow.taskMode 完全一致：

- PRODUCT_CREATIVE：从商品事实和素材证据中产生不同创意方向，不默认套用"痛点→展示→细节→CTA"固定结构。
- REFERENCE_STORYBOARD：参考视频分析只提供节奏、揭示方式和镜头组织机制，不得复制参考视频的具体人物、商品、场景、字幕或动作序列。
- USER_SCRIPT：用户脚本是核心叙事来源。不同方案可改变视觉证明方式、节奏和素材组织，但不能改变脚本的核心含义。
- CUSTOM_STORYBOARD：用户分镜是主要结构约束。可在不破坏分镜意图的前提下，提出不同的视觉执行方式和节奏策略。

━━━━━━━━
三、方案差异化（反模板）
━━━━━━━━

type 只是生成后的内部策略标签，不是创意的起点。允许多个方案具有相同 type，但核心创意机制必须明显不同。禁止先选 type 再套固定模板。

任意两个方案至少需要在以下维度中的 4 个维度存在本质差异：受众切入角度 / 开场钩子机制 / 说服路径 / 素材使用方式 / 节奏组织 / 视觉证明方式 / 人物与空间关系 / 结尾机制。

以下不算真实差异：只更换标题、字幕、场景名称或形容词；保持相同镜头结构只换 type；相同动作机位顺序分别包装成不同视频类型。

应避免的常见同质化套路（可作为局部手段，但不能成为所有方案的共同主结构）：正面站立整理衣摆 / 双手插兜面对镜头 / 原地缓慢转身 / 向镜头走两步 / 背对镜头定格 / 全部镜头中全身居中 / "正面展示→转身→背面展示→细节→CTA" / "痛点→产品→效果→CTA"。

从 assetAnalysis.analysisText 中理解素材独有的创意机会和应避免的套路组合，优先使用可被现有素材证明的卖点。

━━━━━━━━
四、字段规范
━━━━━━━━

type：pain_point_solution / before_after / review / product_showcase / ugc_style / tutorial

title：中文方案标题，准确表达核心创意，不写"高级感展示"等空泛标题。

hook：中文开场钩子（最长 200 字），描述真实开场机制，不编造效果或承诺。

structure：中文结构描述，说明信息推进顺序、视觉机制、素材使用和证明逻辑。不写"Hook→Body→CTA"等空泛结构，不写成逐镜头分镜。

reason：中文推荐理由，说明基于什么用户意图、使用了哪些素材证据、与其他方案的差异。

estimatedDuration：8-60 的整数（秒），优先遵守 workflow.durationSeconds。

score：0-100 的整数，综合用户意图匹配度、证据支持度、素材可执行性、差异化程度和成本可控性。不要让所有方案得相近的高分。

taskMode：必须与输入的 workflow.taskMode 完全一致。

requiredAssets：数组，只能使用：product_front / product_back / product_detail / model_reference / scene_reference / outfit_reference / reference_video。只列出真正需要的素材角色，不因素材存在就强制列入。

estimatedCostTier：cheap / moderate / expensive。综合考虑场景复杂度、关键帧数量、视频片段生成难度和动作连续性要求。

━━━━━━━━
五、输出
━━━━━━━━

所有 title、hook、structure、reason 使用中文。只返回合法 JSON，不输出 Markdown 或解释文字：

{"plans": [{"type": "...", "title": "...", "hook": "...", "structure": "...", "reason": "...", "estimatedDuration": 20, "score": 85, "taskMode": "...", "requiredAssets": [...], "estimatedCostTier": "..."}]}"""
        task_mode = ""
        if isinstance(creative_context, dict):
            workflow = creative_context.get("workflow", {})
            if isinstance(workflow, dict):
                task_mode = workflow.get("taskMode", "")
            user_request = creative_context.get("userRequest", {})
            if isinstance(user_request, dict):
                script_text = user_request.get("scriptText", "")
                storyboard_text = user_request.get("storyboardText", "")
                if task_mode == "USER_SCRIPT" and script_text:
                    creative_context = {
                        **creative_context,
                        "_primaryInput": f"用户脚本（最高优先级创作输入）：\n{script_text}",
                    }
                elif task_mode == "CUSTOM_STORYBOARD" and storyboard_text:
                    creative_context = {
                        **creative_context,
                        "_primaryInput": f"用户分镜结构（最高优先级创作输入）：\n{storyboard_text}",
                    }

        user_prompt = f"""请使用以下创意上下文生成 3-5 个视频方案。

优先级：
1. _primaryInput（如果存在）— 用户最核心的创作意图，所有方案须能追溯到此意图
2. userRequest — 用户偏好（confirmed > parsed > rawPrompt > description，缺失则跳过）
3. assetAnalysis.analysisText — 素材证据和约束的自然语言描述
4. productProfile — 商品客观事实
5. workflow — taskMode、目标时长

如果 _primaryInput 存在：它是创作意图的核心来源，但不是商品事实来源。productProfile 和 assetAnalysis 仍然用于确认商品事实和素材能力。

如果 _primaryInput 不存在：以 userRequest 中的可用字段为意图来源，不得自行发明用户未提出的核心创作目标。

创意上下文：
{creative_context}"""
        result = validate_and_repair(
            await call_llm("fashion_plans", system_prompt, user_prompt),
            CreativePlanResult,
        ).model_dump()

    activity.logger.info("Fashion plans generated: count=%d", len(result.get("plans", [])))
    return result
