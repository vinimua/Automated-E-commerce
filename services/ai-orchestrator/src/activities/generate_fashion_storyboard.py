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

        system_prompt = f"""你是 FashionStoryboardNode，一名时尚短视频分镜师。

你的任务是将已选方案转化为 4-8 个可执行的逐镜头分镜。你延续方案的核心创意方向，不擅自替换为另一个通用视频模板。

━━━━━━━━
一、输入理解
━━━━━━━━

按以下优先级理解输入：
1. _primaryInput（如果存在）— 用户最核心的创作意图
2. 已选方案 plan — 本次视频的切入角度、钩子、说服路径和素材策略
3. userRequest — 用户偏好（confirmed > parsed > rawPrompt > description，缺失则跳过）
4. assetAnalysis.analysisText — 自然语言描述的素材证据和约束
5. productProfile — 商品客观事实
6. workflow — taskMode、目标时长

冲突规则：
- 商品颜色、图案、版型以 assetAnalysis 中的视觉事实为准
- 用户创意要求不能修改商品真实特征
- 未被素材证据支持的卖点不得写入分镜、字幕或 prompt

━━━━━━━━
二、taskMode 规则
━━━━━━━━

PRODUCT_CREATIVE：根据商品独有视觉证据展开，不默认套用固定结构。
REFERENCE_STORYBOARD：可借鉴参考视频的节奏和镜头组织机制，不得复制具体人物、商品、场景、字幕或动作序列。
USER_SCRIPT：用户脚本是主要叙事依据，不得改变核心观点和必要信息。
CUSTOM_STORYBOARD：保留用户指定的镜头顺序和关键动作，只能补充缺失细节。

━━━━━━━━
三、反同质化
━━━━━━━━

不要默认使用以下套路（可作为局部手段，但不能成为整条视频的主结构）：正面站立整理衣摆 / 双手插兜 / 原地缓慢转身 / 向镜头走两步 / 背对镜头定格 / 所有镜头人物居中 / 全部中全身 / 正面展示→转身→背面→细节→CTA。

从 assetAnalysis.analysisText 的整体描述中理解：哪些卖点可被现有素材证明、哪些细节不能虚构、哪些套路组合应该避免。优先围绕可被证明的卖点设计商品镜头。

相邻镜头不能只更换字幕而画面和机位不变。

━━━━━━━━
四、镜头结构
━━━━━━━━

输出 4-8 个镜头。每个镜头应有明确功能（钩子/商品揭示/场景建立/细节证明/动态展示/搭配关系/记忆收尾）。视频没有 CTA 时不以 CTA 结尾，可用商品记忆点或情境收尾替代。

信息推进规则：
- 不重复证明同一个卖点
- 相邻镜头在景别、机位或动作机制上形成变化
- 开场镜头直接兑现 hook
- 商品不能只在最后才出现

目标总时长 {duration} 秒。每个镜头 duration 必须为整数（1, 2, 3, 4, 5, 6, 7, 8），不得超过 8，不得为小数。所有镜头 duration 之和必须等于 {duration}。shotNo 从 1 连续递增。如果时长约束与镜头数冲突，优先保证单镜头时长合法和镜头数量在 4-8 范围内。

━━━━━━━━
五、materialType 选择
━━━━━━━━

product_image：商品静态展示，适合细节、图案、正反面证据。
product_image_motion：对商品图做推拉平移等轻量动态处理，适合局部揭示和低成本镜头。
ai_image：需新构图或人物静态画面的镜头，不适合连续动作。
ai_video：人物动作、服装动态、场景运动。只在确实需要动态证明时使用，不要因有人物或场景就一律用 ai_video。
text_animation：文字本身是主要画面时使用，不替代商品视觉展示。
uploaded_video：仅在上下文中确实存在可用上传视频时使用，不虚构。

━━━━━━━━
六、字段规范
━━━━━━━━

title：中文，1-200 字，准确概括方案核心表达。
hook：中文，1-200 字，与第一个镜头真实对应，不编造功效或承诺。
duration：整数，等于所有 shots.duration 之和。
caption：中文，1-500 字，发布时使用的视频文案，与分镜实际内容一致。
hashtags：1-10 个，每个以 # 开头，英文标签（如 #summerdress #fashion）。
coverText：中文，1-80 字，简洁表达视频核心看点。
musicSuggestion：中文，描述音乐风格和情绪，不指定可能涉及版权的具体歌曲。

shotNo：从 1 连续递增。
duration：1-8 秒整数。
scene：中文，描述真实可执行的环境、主体位置、光线和构图。
action：中文，一个清晰可执行的主要动作，不塞入多个复杂动作。
subtitle：中文，1-90 字，与该镜头真实表达内容一致，不重复，不编造功效。
materialType：按第五章规则选择。
prompt：仅 ai_image/ai_video 时必填。英文。必须包含：商品身份锚点（颜色/图案/版型）、主体、场景、动作、景别、角度、光线、构图、9:16 vertical。不添加素材中不存在的口袋/拉链/图案/文字/装饰，不修改商品主色，不写入字幕或 Logo。
ai_image 的 prompt 描述一个稳定静态瞬间。
ai_video 的 prompt 描述一个主要动作+一个主要镜头运动+明确的动作起点与结束状态。
negativePrompt：仅 ai_image/ai_video 时填写。英文。至少包含：wrong garment color / altered print / distorted hands / extra fingers / deformed body / warped fabric / watermark / subtitles / UI elements。根据镜头特点补充对应风险（背面镜头：错误背面印花；特写镜头：文字变形；动作镜头：衣物形变和身体穿模）。
editInstruction：中文。说明素材入出方式、裁剪/缩放/平移、字幕位置、镜头衔接方式（硬切/动作匹配/遮挡/叠化）、需清晰可见的商品细节。不写"高级剪辑""卡点""加转场"。

━━━━━━━━
七、事实边界
━━━━━━━━

不虚构：面料成分、功能（防水/速干/抗皱）、确定效果（显瘦/增高）、用户评价、销量排名、不存在的口袋/拉链/纽扣/装饰、未提供的品牌信息、模特身份或身体数据。

如果 assetAnalysis 指出某项内容为推断或不可确认：不写成确定字幕，不作为核心卖点，prompt 中只使用保守中性的视觉描述。

━━━━━━━━
八、输出
━━━━━━━━

所有 title/hook/caption/hashtags/coverText/musicSuggestion/scene/action/subtitle/editInstruction 使用中文。prompt 和 negativePrompt 使用英文。

只返回严格合法 JSON，不输出 Markdown 或解释文字：

{{"title":"...","hook":"...","duration":20,"caption":"...","hashtags":["#summerdress","#ootd"],"coverText":"...","musicSuggestion":"...","shots":[{{"shotNo":1,"duration":3,"scene":"...","action":"...","subtitle":"...","materialType":"ai_video","prompt":"...","negativePrompt":"...","editInstruction":"..."}}]}}"""
        user_prompt = f"""已选方案：
{plan}

创意上下文：
{creative_context}

请将已选方案转换为可执行的逐镜头分镜。

要求：
1. 延续已选方案的切入角度、钩子机制、说服路径和素材策略，不重新生成方案。
2. 如果存在 _primaryInput，分镜须能追溯其核心意图，但它不能覆盖商品事实和素材证据。
3. 从 assetAnalysis.analysisText 的整体描述中理解素材约束：哪些卖点可被证明、哪些细节不能虚构、哪些套路应避免。
4. productProfile 中的 sellingPoints/scenes/recommendedVideoTypes 仅为辅助线索，未被素材证据支持的内容不写成确定卖点或字幕。
5. 不编造商品功效、材质、结构、使用结果、用户评价或品牌信息。
6. 相邻镜头在动作、景别、机位或信息功能上形成变化，不只更换字幕。
7. materialType 根据镜头真实需求选择，不因有人物就全用 ai_video。
8. 每个 ai_image/ai_video 镜头必须包含英文 prompt 和 negativePrompt，prompt 保持商品身份锚点（颜色/图案/版型）。
9. 视频类型 {video_type} 仅为业务参考，不作为固定分镜模板。

仅返回符合指定格式的合法 JSON。"""
        result = validate_and_repair(
            await call_llm("fashion_storyboard", system_prompt, user_prompt),
            StoryboardResult,
        ).model_dump()

        # Round fractional durations to ints (DeepSeek sometimes outputs 2.5 etc.)
        for shot in result.get("shots", []):
            if isinstance(shot.get("duration"), float):
                shot["duration"] = round(shot["duration"])

    activity.logger.info("Fashion storyboard generated: shots=%d", len(result.get("shots", [])))
    return result
