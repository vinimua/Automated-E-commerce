"""Activity: Analyze reference video — URL-first approach, no fabrication fallback."""

import base64
import logging
import tempfile
from pathlib import Path

from temporalio import activity
from src.schemas.ai_outputs import ReferenceVideoAnalysis

log = logging.getLogger(__name__)

MAX_VIDEO_SIZE_BYTES = 100 * 1024 * 1024  # 100 MB
MAX_BASE64_PAYLOAD_BYTES = 20 * 1024 * 1024  # 20 MB — keep base64 payloads reasonable


async def _download_video(url: str) -> bytes:
    """Download video from URL with browser-like headers for CDN bypass."""
    import httpx
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Accept": "*/*",
        "Referer": "https://cloud.video.taobao.com/",
    }
    # Use retries=0 to avoid httpx connection-pool issues on Windows→localhost
    transport = httpx.AsyncHTTPTransport(retries=0)
    async with httpx.AsyncClient(transport=transport, timeout=120, follow_redirects=True, headers=headers) as client:
        response = await client.get(url)
        response.raise_for_status()
        content = response.content
        if len(content) > MAX_VIDEO_SIZE_BYTES:
            raise ValueError(f"Video too large: {len(content)} bytes (max {MAX_VIDEO_SIZE_BYTES})")
        log.info("Video downloaded: url=%s, size=%d bytes", url[:80], len(content))
        return content


def _encode_base64(data: bytes) -> str:
    """Base64-encode video bytes into a data URL."""
    b64 = base64.b64encode(data).decode("ascii")
    return f"data:video/mp4;base64,{b64}"


async def _call_vision_with_video_url(
    task_type: str, system_prompt: str, user_prompt: str, video_url: str
) -> dict:
    """Call the vision LLM with a video URL via Volcengine Responses API."""
    from src.config import settings
    from src.services.llm_service import _call_volcengine_responses

    is_data_url = video_url.startswith("data:")
    log.info("Vision LLM video request: model=%s, url_type=%s, url_len=%d",
             settings.vision_llm_model, "data_url" if is_data_url else "remote_url",
             len(video_url))

    return await _call_volcengine_responses(
        task_type=task_type,
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        image_urls=[],
        video_url=video_url,
        model=settings.vision_llm_model,
    )


async def _analyze_with_frames(
    task_type: str, system_prompt: str, user_prompt: str, video_bytes: bytes
) -> dict:
    """Fallback: extract frames from video and analyze via image_url.

    Since we may not have ffmpeg/opencv available everywhere, this
    attempts frame extraction using available tools and sends the
    frames as images to the vision LLM.
    """
    from src.config import settings
    from src.services.llm_service import _call_volcengine_responses

    # Try to extract frames using available tools
    frames_base64 = await _extract_frames(video_bytes)
    if not frames_base64:
        raise RuntimeError("Frame extraction produced no usable frames")

    frame_data_uris = [f"data:image/jpeg;base64,{fb}" for fb in frames_base64]

    log.info("Vision LLM frame analysis: model=%s, frames=%d", settings.vision_llm_model, len(frames_base64))

    return await _call_volcengine_responses(
        task_type=task_type,
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        image_urls=frame_data_uris,
        video_url=None,
        model=settings.vision_llm_model,
    )


async def _extract_frames(video_bytes: bytes, max_frames: int = 8) -> list[str]:
    """Extract key frames from video bytes as base64 JPEG strings.

    Tries multiple methods in order: opencv, ffmpeg, then Pillow-based fallback.
    Returns empty list if no method works.
    """
    frames = []

    # Method 1: Try opencv (most reliable)
    try:
        import cv2
        import numpy as np
        import tempfile, os

        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as f:
            f.write(video_bytes)
            tmp_path = f.name

        try:
            cap = cv2.VideoCapture(tmp_path)
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            if total_frames <= 0:
                cap.release()
                raise ValueError("Cannot read video frame count")

            # Sample evenly spaced frames
            frame_indices = [int(i * total_frames / (max_frames + 1)) for i in range(1, max_frames + 1)]
            for idx in frame_indices:
                cap.set(cv2.CAP_PROP_POS_FRAMES, idx)
                ret, frame = cap.read()
                if ret:
                    _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 75])
                    frames.append(base64.b64encode(buf).decode("ascii"))
            cap.release()
        finally:
            os.unlink(tmp_path)

        if frames:
            log.info("Frames extracted via opencv: %d frames", len(frames))
            return frames
    except ImportError:
        log.debug("opencv not available for frame extraction")
    except Exception as e:
        log.warning("opencv frame extraction failed: %s", e)

    # Method 2: Try ffmpeg subprocess
    try:
        import subprocess, tempfile, os

        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as f:
            f.write(video_bytes)
            input_path = f.name

        output_pattern = input_path + "_frame_%03d.jpg"
        try:
            subprocess.run(
                ["ffmpeg", "-y", "-i", input_path, "-vf", f"fps=1/{max(1, max_frames)}",
                 "-frames:v", str(max_frames), "-q:v", "5", output_pattern],
                capture_output=True, timeout=30, check=True,
            )
            for i in range(1, max_frames + 1):
                frame_path = output_pattern.replace("%03d", f"{i:03d}")
                if os.path.exists(frame_path):
                    with open(frame_path, "rb") as ff:
                        frames.append(base64.b64encode(ff.read()).decode("ascii"))
                    os.unlink(frame_path)
        finally:
            os.unlink(input_path)
            # Clean up any remaining frame files
            for i in range(1, max_frames + 2):
                p = output_pattern.replace("%03d", f"{i:03d}")
                if os.path.exists(p):
                    os.unlink(p)

        if frames:
            log.info("Frames extracted via ffmpeg: %d frames", len(frames))
            return frames
    except Exception as e:
        log.warning("ffmpeg frame extraction failed: %s", e)

    return []


@activity.defn
async def analyze_reference_video(task_id: str, reference_url: str) -> dict:
    """Analyze a reference video using the vision LLM.

    Strategy (in order):
    1. Pass the URL directly to the vision LLM — the model's servers download it.
       This works best for China-hosted CDN URLs accessible from Volcengine servers.
    2. If URL approach times out (URL not reachable from model servers), download the
       video ourselves with browser headers and send as base64 data URL.
    3. If base64 approach also fails, raise an error — NEVER fabricate an analysis.

    Returns a ReferenceVideoAnalysis dict on success.
    """
    from src.services.llm_service import _is_fake_mode

    if _is_fake_mode("reference_video_analysis"):
        from src.services.llm_service import get_fashion_fixture
        result = get_fashion_fixture("reference_video_analysis")
        activity.logger.info("Reference video analysis (fake): title=%s, shots=%d",
                             result.get("title"), len(result.get("shots", [])))
        return result

    from src.services.validation_pipeline import validate_and_repair

    system_prompt = r"""你是 ReferenceVideoAnalysisNode，一名服装短视频结构分析师。

你的核心任务：还原参考视频的真实镜头结构，提取可迁移的抽象创作机制，为后续 Creative Plan 提供结构依据。你只分析参考视频本身，不生成新方案、分镜、Prompt 或字幕。

━━━━━━━━
一、证据规则
━━━━━━━━

所有结论必须来自视频实际内容。使用四级标注：

- 【可见】画面可直接确认
- 【可听】音频可清楚辨认
- 【推测】上下文合理推断，但无法确认
- 【不可确认】质量/遮挡/采样不足导致无法判断

硬约束：
- 看不清的文字不补全，听不清的口播不编造
- 字幕字段只记录真实可见或清楚可听的内容，无内容时填 null
- 不根据文件名、标题或外部信息推测视频内容
- 不识别真人身份，不根据画面判定商品材质成分

━━━━━━━━
二、镜头划分
━━━━━━━━

一个镜头 = 一个连续的视觉表达单元。

应拆为新镜头：硬切 / 叠化或遮挡转场完成 / 场景或主体明显变化 / 景别通过剪辑出现跳变 / 叙事功能发生明确变化

不应拆镜：同一连续拍摄中的推拉摇移 / 同一人物连续完成一个动作 / 轻微构图变化 / 仅字幕变化而画面和叙事功能不变

━━━━━━━━
三、时间轴
━━━━━━━━

1. shotNo 从 1 连续递增
2. startTime / endTime 用秒，最多保留 1 位小数
3. duration = endTime - startTime
4. 相邻镜头不重叠，无无故空档
5. 首个镜头从视频开头开始，末镜头 endTime 接近总时长
6. 存在黑场或片头尾空白时，作为实际时间段处理或在 riskTips 说明
7. 时间点无法精确判断时可估算，但须在 riskTips 注明

━━━━━━━━
四、字段定义
━━━━━━━━

title — 视频描述性标题。有明确标题时忠实转写，无标题时根据内容概括，不把概括声称为原视频标题。

duration — 视频总时长（秒）。

hook — 开场吸引机制。有明确字幕/口播时记录核心内容；无文案时描述视觉钩子机制（如"通过连续快速换装制造视觉反差"）。不编造不存在的文案。

structure — 有序结构角色数组，基于真实信息功能。可用角色：hook / setup / pain_point / product_intro / outfit_reveal / detail_proof / comparison / demonstration / social_proof / lifestyle / transition / payoff / cta / ending。视频没有 CTA 时不添加 CTA。

shots — 按时间顺序的镜头数组。每镜头包含：
  - shotNo（必填）
  - startTime / endTime / duration（必填，单位秒）
  - scene：只描述真实可见的环境、主体和构图
  - action：当前镜头中实际发生的主要动作和商品展示行为
  - camera（可选）：景别（特写/近景/中景/中全景/全景）+ 角度（平视/俯拍/仰拍/侧面/背面）+ 运动（固定/推近/拉远/跟拍/横移/摇镜/手持）+ 构图。不写"高级运镜""电影感"
  - transition（可选）：进入下一镜的实际转场方式（硬切/动作匹配/遮挡转场/叠化/闪白/推拉匹配）。末镜头可为 null。无法判断时写"无法确认"
  - subtitle（可选|null）：仅真实可见字幕或清晰可辨口播核心文本。无法确认时填 null
  - structureRole（可选）：该镜头的叙事功能角色

reusablePatterns — 抽象创作机制数组，不照搬表面内容。

  正确（机制层面）：
  - 先展示局部细节再拉远揭示完整穿搭，形成信息递进
  - 利用人物经过前景完成自然遮挡转场
  - 前三秒短镜头快切后进入较长商品展示，制造节奏变化
  - 先给结果画面再回到过程说明，形成结果前置结构

  错误（表面复制）：
  - 在同一家咖啡厅拍摄 / 使用同一个模特 / 穿同样的白色裙子 / 所有视频都采用转身展示

  每条推荐格式：机制 + 解决什么表达问题 + 迁移时需改变什么表面元素。

riskTips — 记录：时间点为近似值 / 音频不可辨认 / 字幕被遮挡 / 镜头边界无法精确判断 / 包含品牌 Logo 或可识别人物 / 参考结构存在模板化风险 / 与服装展示关系较弱。不编造法律结论。

━━━━━━━━
五、反同质化
━━━━━━━━

必须区分两个层次：

可迁移（抽象到 Creative Plan）：信息顺序 / 节奏变化 / 镜头长短关系 / 揭示机制 / 转场机制 / 证据展示方式 / 叙事功能关系

不可照搬：具体人物 / 商品 / 品牌 Logo / 场景布置 / 相同动作序列 / 相同机位序列 / 相同字幕 / 相同构图和结尾

如果参考视频本身高度依赖通用套路（正面站立→转身→走向镜头→咖啡厅暖光→全身居中），在 riskTips 中指出模板化风险，并提示 Creative Plan 应保留抽象机制但改变动作、空间关系和机位。

━━━━━━━━
六、输出
━━━━━━━━

只返回合法 JSON，不输出 Markdown 或解释文字。所有文本字段使用中文。可选字段无法确认时返回 null，不用空字符串。"""

    user_prompt = r"""请完整分析随本消息提供的参考视频。

本次任务目标是还原视频实际存在的分镜结构，并提取可迁移的抽象创作机制，而不是复制具体人物、商品、场景、字幕和动作顺序。

请重点完成：
1. 按真实剪辑点和叙事功能划分镜头
2. 输出每个镜头的开始时间、结束时间和时长
3. 描述每个镜头的场景、主体动作、商品展示行为
4. 识别景别、机位、镜头运动和构图
5. 识别实际存在的转场
6. 只记录真实可见或清楚可听的字幕与口播
7. 判断每个镜头承担的结构角色
8. 提取可以迁移的叙事、节奏、揭示和转场机制
9. 在 riskTips 中指出分析不确定性、版权识别风险和模板化照搬风险

不要生成新的创意方案、分镜、字幕或生成提示词。
所有文本使用中文。
仅返回符合指定 Schema 的合法 JSON，不要输出 Markdown。"""

    last_error = None

    # ── Strategy 1: Pass URL directly ──
    # Volcengine servers are in China and can access most Chinese CDNs directly.
    # This avoids the huge base64 payload entirely.
    try:
        activity.logger.info("Trying URL-first approach: task_id=%s", task_id)
        raw_result = await _call_vision_with_video_url(
            "reference_video_analysis", system_prompt, user_prompt, reference_url
        )
        result = validate_and_repair(raw_result, ReferenceVideoAnalysis).model_dump()
        activity.logger.info("Reference video analysis (URL-first) complete: title=%s, shots=%d",
                             result.get("title"), len(result.get("shots", [])))
        return result
    except Exception as e:
        last_error = e
        activity.logger.warning(
            "URL-first approach failed for task_id=%s: %s. Will try download+base64.",
            task_id, str(e)[:200]
        )

    # ── Strategy 2: Download + base64 data URL ──
    # Some CDNs require specific headers (Referer, User-Agent) that the vision
    # model servers won't send. We download ourselves and embed as base64.
    downloaded_bytes = None  # saved for potential reuse in strategy 3
    try:
        activity.logger.info("Trying download+base64 approach: task_id=%s", task_id)
        downloaded_bytes = await _download_video(reference_url)

        # Check if base64 payload is reasonable
        if len(downloaded_bytes) > MAX_BASE64_PAYLOAD_BYTES:
            raise ValueError(
                f"Video too large for base64 embedding: {len(downloaded_bytes)} bytes "
                f"(max {MAX_BASE64_PAYLOAD_BYTES}). The video must be downloaded "
                f"by the model server — try a smaller file or a different URL."
            )

        video_data_url = _encode_base64(downloaded_bytes)
        raw_result = await _call_vision_with_video_url(
            "reference_video_analysis", system_prompt, user_prompt, video_data_url
        )
        result = validate_and_repair(raw_result, ReferenceVideoAnalysis).model_dump()
        activity.logger.info("Reference video analysis (download+base64) complete: title=%s, shots=%d",
                             result.get("title"), len(result.get("shots", [])))
        return result

    except Exception as e:
        last_error = e
        activity.logger.warning(
            "Download+base64 approach failed for task_id=%s: %s. Will try frame extraction.",
            task_id, str(e)[:200]
        )

    # ── Strategy 3: Download + extract frames → image_url ──
    # If video_url doesn't work (e.g. model rejects base64 videos above certain size),
    # extract key frames and analyze them as images. We lose temporal info but still
    # get real visual analysis from actual frames.
    try:
        activity.logger.info("Trying frame extraction approach: task_id=%s", task_id)
        if downloaded_bytes is None:
            downloaded_bytes = await _download_video(reference_url)
        raw_result = await _analyze_with_frames(
            "reference_video_analysis", system_prompt, user_prompt, downloaded_bytes
        )
        result = validate_and_repair(raw_result, ReferenceVideoAnalysis).model_dump()
        activity.logger.info("Reference video analysis (frames) complete: title=%s, shots=%d",
                             result.get("title"), len(result.get("shots", [])))
        return result

    except Exception as e:
        last_error = e
        activity.logger.error(
            "Frame extraction approach also failed for task_id=%s: %s",
            task_id, str(e)[:200]
        )

    # ── All strategies exhausted — fail honestly ──
    # We NEVER fabricate an analysis via text LLM. If the vision model can't
    # process the video, the task should fail with a clear error so the user
    # knows the analysis didn't work, instead of seeing made-up data.
    error_msg = (
        f"All 3 video analysis strategies failed for task {task_id}. "
        f"Last error: {last_error}"
    )
    activity.logger.error(error_msg)
    raise RuntimeError(error_msg)
