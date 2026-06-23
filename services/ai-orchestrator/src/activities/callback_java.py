"""Activity: Send callback to Java API."""

from temporalio import activity
from src.schemas.ai_outputs import CallbackPayload, CallbackError
from src.services.callback_service import send_callback


@activity.defn
async def callback_java(payload: dict) -> str:
    """Send AI stage result callback to Java. Payload must match CallbackPayload schema."""
    cb = CallbackPayload.model_validate(payload)

    success = await send_callback(cb)
    if not success:
        activity.logger.error("Callback failed after all retries: taskId=%s, stage=%s",
                              cb.taskId, cb.stage)
        raise Exception(f"Callback failed: taskId={cb.taskId}, stage={cb.stage}")

    return f"callback_ok:{cb.stage}"


def build_callback_payload(
    task_id: str,
    stage: str,
    status: str,
    next_task_status: str | None,
    **stage_data,
) -> dict:
    """Build a CallbackPayload dict for the callback_java activity."""
    payload = {
        "taskId": task_id,
        "schemaVersion": "1.0.0",
        "stage": stage,
        "status": status,
        **stage_data,
    }
    if next_task_status is not None:
        payload["nextTaskStatus"] = next_task_status
    return payload


def build_failed_callback_payload(
    task_id: str,
    stage: str,
    error_code: str,
    error_message: str,
    retryable: bool = False,
) -> dict:
    """Build a failed callback payload."""
    return {
        "taskId": task_id,
        "schemaVersion": "1.0.0",
        "stage": stage,
        "status": "failed",
        "nextTaskStatus": "failed",
        "error": CallbackError(
            errorCode=error_code,
            errorMessage=error_message,
            failedStage=stage,
            retryable=retryable,
        ).model_dump(),
    }
