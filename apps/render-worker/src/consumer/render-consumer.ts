/**
 * RabbitMQ Consumer: video.render.queue
 *
 * 消费渲染任务，执行 11 步校验 → Remotion 渲染 → FFmpeg → COS 上传 → Java 回调
 */

import amqp from "amqplib";
import { validateRenderManifest } from "../services/manifest-validator";
import { renderVideo } from "../services/renderer";
import { uploadToCOS } from "../services/cos-uploader";
import { callbackJava, CallbackPayload } from "../services/java-callback";

const QUEUE = "video.render.queue";
const PREFETCH = 1; // Process one task at a time

export interface RenderMessage {
  taskId: string;
  renderTaskId: string;
  correlationId: string;
  renderManifest: Record<string, unknown>;
  callbackUrl: string;
}

export async function consumeRenderQueue(rabbitmqUrl: string) {
  const connection = await amqp.connect(rabbitmqUrl);
  const channel = await connection.createChannel();

  await channel.assertQueue(QUEUE, { durable: true });
  await channel.prefetch(PREFETCH);

  console.log(`[render-consumer] Listening on queue: ${QUEUE}`);

  channel.consume(QUEUE, async (msg) => {
    if (!msg) return;

    let message: RenderMessage;
    try {
      message = JSON.parse(msg.content.toString());
    } catch {
      console.error("[render-consumer] Invalid JSON message, rejecting (no requeue)");
      channel.reject(msg, false);
      return;
    }

    const { taskId, renderTaskId, correlationId, renderManifest, callbackUrl } = message;
    console.log(`[render-consumer] Processing: taskId=${taskId} renderTaskId=${renderTaskId}`);

    try {
      // Step 1-6: Validate manifest through 11-step pipeline
      const validation = await validateRenderManifest(renderManifest);
      if (!validation.valid) {
        await callbackJava(callbackUrl, {
          taskId,
          renderTaskId,
          correlationId,
          status: "failed",
          error: {
            errorCode: "MANIFEST_VALIDATION_FAILED",
            errorMessage: validation.error || "Manifest validation failed",
            failedStage: "validation",
            retryable: false,
          },
        });
        channel.ack(msg);
        return;
      }

      // Step 7-12: Render
      const renderResult = await renderVideo(renderManifest);

      // Step 13-15: Upload
      const uploadResult = await uploadToCOS(renderResult);

      // Step 16: Callback Java
      const callbackPayload: CallbackPayload = {
        taskId,
        renderTaskId,
        correlationId,
        status: "completed",
        videoUrl: uploadResult.videoUrl,
        coverUrl: uploadResult.coverUrl,
        duration: renderResult.duration,
        resolution: "1080x1920",
        manifestVersion: "1.0.0",
        renderLog: {
          template: renderManifest.template,
          renderTime: renderResult.renderTimeMs,
          fileSize: uploadResult.fileSizeBytes,
        },
      };

      await callbackJava(callbackUrl, callbackPayload);
      console.log(`[render-consumer] Completed: taskId=${taskId}`);
      channel.ack(msg);
    } catch (error: any) {
      console.error(`[render-consumer] Failed: taskId=${taskId}`, error.message);

      // Notify Java of failure
      try {
        await callbackJava(callbackUrl, {
          taskId,
          renderTaskId,
          correlationId,
          status: "failed",
          error: {
            errorCode: "RENDER_FAILED",
            errorMessage: error.message,
            failedStage: "rendering",
            retryable: true,
          },
        });
      } catch (cbError) {
        console.error("[render-consumer] Callback failed:", cbError);
      }

      // Requeue for retry
      channel.reject(msg, true);
    }
  });
}
