/**
 * TikTok Shop AI Video — Render Worker
 *
 * Consumes RabbitMQ render tasks, renders Remotion templates,
 * produces MP4 via FFmpeg, uploads to COS, and calls back Java.
 */

import dotenv from "dotenv";
dotenv.config();

import { consumeRenderQueue } from "./consumer/render-consumer";

const RABBITMQ_URL =
  process.env.RABBITMQ_URL || "amqp://tk_user:tk_dev_password@localhost:5672";

async function main() {
  console.log("[render-worker] Starting Render Worker...");
  console.log(`[render-worker] RabbitMQ: ${RABBITMQ_URL}`);

  try {
    await consumeRenderQueue(RABBITMQ_URL);
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
