import { z } from "zod";

export const KeyframeStatusSchema = z.enum([
  "draft",
  "generating",
  "generated",
  "uploaded",
  "confirmed",
  "rejected",
  "failed",
]);

export const KeyframeSourceSchema = z.enum(["user_upload", "existing_asset", "ai_generated"]);
export const ImagePurposeSchema = z.enum(["first_frame", "last_frame", "reference", "product_detail"]);

export const KeyframePromptSchema = z
  .object({
    taskId: z.string().uuid(),
    shotNo: z.number().int().min(1).max(12),
    imagePurpose: ImagePurposeSchema.default("first_frame"),
    prompt: z.string().trim().min(1).max(2000),
    negativePrompt: z.string().trim().max(1000).optional(),
    referenceAssetIds: z.array(z.string().uuid()).default([]),
  })
  .strict();

export const UploadedKeyframeSchema = z
  .object({
    taskId: z.string().uuid(),
    shotNo: z.number().int().min(1).max(12),
    source: z.literal("user_upload"),
    imagePurpose: ImagePurposeSchema.default("first_frame"),
    assetId: z.string().uuid(),
    imageUrl: z.string().url(),
  })
  .strict();

export const ConfirmKeyframeSchema = z
  .object({
    keyframeId: z.string().uuid(),
    status: z.union([z.literal("confirmed"), z.literal("rejected")]),
    feedback: z.string().trim().max(1000).optional(),
  })
  .strict();

export type KeyframePromptInput = z.infer<typeof KeyframePromptSchema>;
export type UploadedKeyframeInput = z.infer<typeof UploadedKeyframeSchema>;
export type ConfirmKeyframeInput = z.infer<typeof ConfirmKeyframeSchema>;
