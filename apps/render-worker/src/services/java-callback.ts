/**
 * Java API callback client.
 * Retries up to 3 times on failure.
 */

export interface CallbackPayload {
  taskId: string;
  renderTaskId: string;
  correlationId: string;
  status: "completed" | "failed";
  videoUrl?: string;
  coverUrl?: string;
  duration?: number;
  resolution?: string;
  manifestVersion?: string;
  renderLog?: Record<string, unknown>;
  error?: {
    errorCode: string;
    errorMessage: string;
    failedStage: string;
    retryable: boolean;
  };
}

const MAX_RETRIES = 3;
const RETRY_DELAY_MS = 2000;

async function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function callbackJava(
  callbackUrl: string,
  payload: CallbackPayload
): Promise<void> {
  const body = JSON.stringify(payload);

  for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
    try {
      const response = await fetch(callbackUrl, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-Internal-Service-Token":
            process.env.INTERNAL_SERVICE_TOKEN || "internal-dev-token-change-in-production",
        },
        body,
      });

      if (response.ok) {
        console.log(`[java-callback] Success: taskId=${payload.taskId}`);
        return;
      }

      const responseText = await response.text();
      console.warn(
        `[java-callback] Attempt ${attempt}/${MAX_RETRIES} failed (${response.status}): ${responseText}`
      );
    } catch (error: any) {
      console.warn(
        `[java-callback] Attempt ${attempt}/${MAX_RETRIES} error: ${error.message}`
      );
    }

    if (attempt < MAX_RETRIES) {
      await delay(RETRY_DELAY_MS * attempt);
    }
  }

  throw new Error(
    `[java-callback] All ${MAX_RETRIES} attempts failed for taskId=${payload.taskId}`
  );
}
