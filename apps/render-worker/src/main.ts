/**
 * TikTok Shop AI Video — Render Worker
 *
 * Consumes RabbitMQ render tasks, renders Remotion templates,
 * produces MP4 via FFmpeg, uploads to COS, and calls back Java.
 */

import dotenv from "dotenv";
import path from "path";

dotenv.config();
dotenv.config({ path: path.resolve(process.cwd(), "../../.env") });

import { consumeRenderQueue } from "./consumer/render-consumer";
import { config } from "./config";

async function main() {
  console.log("[render-worker] Starting Render Worker...");
  console.log(`[render-worker] RabbitMQ: ${config.rabbitmqUrl}`);
  console.log(`[render-worker] Temp dir: ${config.tempDir}`);

  try {
    await consumeRenderQueue(config.rabbitmqUrl);
  } catch (error) {
    console.error("[render-worker] Fatal error:", error);
    process.exit(1);
  }
}

// Graceful shutdown
process.on("SIGINT", async () => {
  console.log("[render-worker] Shutting down...");
  process.exit(0);
});

process.on("SIGTERM", async () => {
  console.log("[render-worker] Shutting down...");
  process.exit(0);
});

main();
