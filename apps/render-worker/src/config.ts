/**
 * Centralized configuration — loaded from environment variables.
 */
import os from "os";
import path from "path";

export const config = {
  /** RabbitMQ connection URL */
  rabbitmqUrl:
    process.env.RABBITMQ_URL ||
    "amqp://tk_user:tk_dev_password@124.223.200.16:15673",

  /** Tencent COS object storage */
  cos: {
    secretId: process.env.COS_SECRET_ID || "",
    secretKey: process.env.COS_SECRET_KEY || "",
    region: process.env.COS_REGION || "ap-guangzhou",
    bucket: process.env.COS_BUCKET || "",
    cdnDomain: process.env.COS_CDN_DOMAIN || "",
  },

  /** Internal service token for Java callback auth */
  internalServiceToken:
    process.env.INTERNAL_SERVICE_TOKEN ||
    "internal-dev-token-change-in-production",

  /** Temp directory for render artifacts */
  tempDir: process.env.RENDER_TEMP_DIR || path.join(os.tmpdir(), "tk-render-worker"),

  /** Max retries for asset download */
  maxDownloadRetries: 3,

  /** Max render pipeline attempts before final failed callback + ack */
  maxRenderRetries: Number(process.env.MAX_RENDER_RETRIES || "3"),

  /** Download timeout per asset (ms) */
  downloadTimeoutMs: 30_000,

  /** Max file sizes */
  maxVideoSizeBytes: 50 * 1024 * 1024, // 50 MB
  maxImageSizeBytes: 10 * 1024 * 1024, // 10 MB

  /** Render queue name */
  renderQueue: "video.render.queue",
} as const;
