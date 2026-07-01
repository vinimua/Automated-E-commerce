import { z } from "zod";

export const VideoClipStatusSchema = z.enum([
  "draft",
  "generating",
  "generated",
  "uploaded",
  "confirmed",
  "rejected",
  "failed",
]);

export const VideoClipSourceSchema = z.enum(["user_upload", "ai_generated"]);

export const VideoClipPromptSchema = z
  .object({
    taskId: z.string().uuid(),
    shotNo: z.number().int().min(1).max(12),
    duration: z.number().int().min(1).max(8),
    prompt: z.string().trim().min(1).max(2000),
    negativePrompt: z.string().trim().max(1000).optional(),
    firstFrameKeyframeId: z.string().uuid().optional(),
    lastFrameKeyframeId: z.string().uuid().optional(),
  })
  .strict();

export const UploadedVideoClipSchema = z
  .object({
    taskId: z.string().uuid(),
    shotNo: z.number().int().min(1).max(12),
    source: z.literal("user_upload"),
    assetId: z.string().uuid(),
    clipUrl: z.string().url(),
    duration: z.number().min(0.5).max(8),
  })
  .strict();

export const ConfirmVideoClipSchema = z
  .object({
    clipId: z.string().uuid(),
    status: z.union([z.literal("confirmed"), z.literal("rejected")]),
    feedback: z.string().trim().max(1000).optional(),
  })
  .strict();

export type VideoClipPromptInput = z.infer<typeof VideoClipPromptSchema>;
export type UploadedVideoClipInput = z.infer<typeof UploadedVideoClipSchema>;
export type ConfirmVideoClipInput = z.infer<typeof ConfirmVideoClipSchema>;
