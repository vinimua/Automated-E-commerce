"""HTTP callback client to Java API — POST /api/ai-callbacks/{taskId} with retry."""

import asyncio
import logging
import httpx
from src.config import settings
from src.schemas.ai_outputs import CallbackPayload

log = logging.getLogger(__name__)

MAX_RETRIES = settings.max_callback_retries
RETRY_BASE_DELAY = 2  # seconds


async def send_callback(payload: CallbackPayload) -> bool:
    """Send AI callback to Java. Raises with details after all retries fail."""
    url = f"{settings.java_api_base_url}/api/ai-callbacks/{payload.taskId}"
    body = payload.model_dump(mode="json")
    last_error = "no attempts made"

    for attempt in range(1, MAX_RETRIES + 1):
        try:
            async with httpx.AsyncClient(timeout=30.0) as client:
                response = await client.post(
                    url,
                    json=body,
                    headers={
                        "Content-Type": "application/json",
                        "X-Internal-Service-Token": settings.internal_service_token,
                    },
                )
                if response.is_success:
                    log.info("Callback succeeded: taskId=%s, stage=%s, status=%s, attempt=%d",
                             payload.taskId, payload.stage, payload.status, attempt)
                    return True
                last_error = (
                    f"HTTP {response.status_code} from {url}: "
                    f"{response.text[:500]}"
                )
                log.warning("Callback HTTP %d: taskId=%s, attempt=%d/%d, body=%s",
                            response.status_code, payload.taskId, attempt, MAX_RETRIES,
                            response.text[:300])
        except Exception as e:
            last_error = f"{type(e).__name__} calling {url}: {e}"
            log.warning("Callback error: taskId=%s, attempt=%d/%d, error=%s",
                        payload.taskId, attempt, MAX_RETRIES, e)

        if attempt < MAX_RETRIES:
            delay = RETRY_BASE_DELAY * attempt
            await asyncio.sleep(delay)

    log.error("Callback FAILED after %d attempts: taskId=%s, stage=%s",
              MAX_RETRIES, payload.taskId, payload.stage)
    raise RuntimeError(
        f"Callback failed after {MAX_RETRIES} attempts: "
        f"taskId={payload.taskId}, stage={payload.stage}, lastError={last_error}"
    )
