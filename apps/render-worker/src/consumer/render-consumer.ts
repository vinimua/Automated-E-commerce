/**
 * RabbitMQ Consumer: video.render.queue
 *
 * Pipeline:
 *   parse → validate manifest → download assets → render → upload to COS → callback Java
 */
import amqp from "amqplib";
import fs from "fs";
import path from "path";
import { config } from "../config";
import { validateRenderManifest } from "../services/manifest-validator";
import { downloadAssets } from "../services/asset-downloader";
import { renderVideo } from "../services/renderer";
import { uploadToCOS } from "../services/cos-uploader";
import { callbackJava, CallbackPayload } from "../services/java-callback";

const QUEUE = config.renderQueue;
const PREFETCH = 5; // Process up to 5 renders concurrently (fake renders are cheap)
const RETRY_HEADER = "x-render-retry-count";

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
    const tempDir = path.join(config.tempDir, taskId, renderTaskId);

    console.log(
      `[render-consumer] Processing: taskId=${taskId} renderTaskId=${renderTaskId} template=${renderManifest.template}`
    );

    try {
      // ── Step 1: Validate manifest ──
      const validation = await validateRenderManifest(renderManifest);
      if (!validation.valid) {
        console.error(`[render-consumer] Manifest validation failed: ${validation.error}`);
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
      console.log("[render-consumer] Manifest validated OK");

      // ── Step 2: Download assets ──
      const assets = (renderManifest.assets || []) as Array<{
        shotNo: number;
        type: string;
        url?: string;
        textContent?: string;
      }>;
      const downloadedAssets = await downloadAssets(assets, taskId, renderTaskId);
      console.log(
        `[render-consumer] Assets downloaded: ${downloadedAssets.length} items`
      );

      // ── Step 3: Render video ──
      const renderResult = await renderVideo(
        renderManifest,
        taskId,
        renderTaskId,
        downloadedAssets
      );

      // ── Step 4: Upload to COS ──
      const uploadResult = await uploadToCOS(
        renderResult.outputPath,
        renderResult.coverPath,
        taskId,
        renderTaskId
      );

      // ── Step 5: Callback Java — success ──
      const callbackPayload: CallbackPayload = {
        taskId,
        videoId: String(renderManifest.videoId),
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

      const retryCount = Number(msg.properties.headers?.[RETRY_HEADER] || 0);
      if (retryCount < config.maxRenderRetries) {
        const nextRetry = retryCount + 1;
        console.warn(
          `[render-consumer] Requeueing taskId=${taskId}, attempt=${nextRetry}/${config.maxRenderRetries}`
        );
        channel.sendToQueue(QUEUE, msg.content, {
          persistent: true,
          contentType: msg.properties.contentType || "application/json",
          headers: {
            ...(msg.properties.headers || {}),
            [RETRY_HEADER]: nextRetry,
          },
        });
        channel.ack(msg);
        return;
      }

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
            retryable: false,
          },
        });
      } catch (cbError) {
        console.error("[render-consumer] Final failure callback failed:", cbError);
      }

      channel.ack(msg);
    } finally {
      // ── Cleanup: remove temp directory ──
      try {
        if (fs.existsSync(tempDir)) {
          fs.rmSync(tempDir, { recursive: true, force: true });
          console.log(`[render-consumer] Cleaned up temp dir: ${tempDir}`);
        }
      } catch (cleanupErr: any) {
        console.warn(`[render-consumer] Temp cleanup failed: ${cleanupErr.message}`);
      }
    }
  });
}
