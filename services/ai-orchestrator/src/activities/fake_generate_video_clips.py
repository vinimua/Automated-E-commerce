"""Activity: fake video clip generation.

M5 uses this deterministic fake provider before a real video model is connected.
The fake provider must preserve the task shape: one input prompt produces one
callback clip, so Java and the frontend can verify missing/failed/regenerate
behavior without provider noise.
"""

from temporalio import activity

from src.config import settings
from src.schemas.ai_outputs import VideoClipGenerationResult


FAKE_VIDEO_URL = "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"


@activity.defn
async def fake_generate_video_clips(task_id: str, prompts: dict, storyboard: dict) -> dict:
    """Generate fake video clips for the requested shots.

    Real video generation is still an M6 concern. Even when video generation is
    enabled accidentally, this activity falls back to deterministic fake output
    until a real provider activity replaces it.
    """
    if settings.enable_video_generation:
        activity.logger.warning("Real video generation is not implemented yet; using deterministic fake clips")

    prompt_items = prompts.get("prompts", [])
    clips = []
    for item in prompt_items:
        shot_no = int(item.get("shotNo") or 0)
        if shot_no <= 0:
            continue

        prompt_text = item.get("prompt") or f"Shot {shot_no} video clip"
        clips.append({
            "shotNo": shot_no,
            "status": "completed",
            "url": f"{FAKE_VIDEO_URL}#task={task_id}&shot={shot_no}",
            "prompt": prompt_text,
            "negativePrompt": item.get("negativePrompt") or "blurry, low quality, watermark, text, jitter",
            "provider": "fake",
            "modelName": "fake-video-provider-v1",
            "duration": int(item.get("duration") or 3),
            "source": "ai_generated",
        })

    result = {"clips": clips}
    VideoClipGenerationResult.model_validate(result)
    activity.logger.info("Fake video clips generated: taskId=%s, count=%d", task_id, len(clips))
    return result
