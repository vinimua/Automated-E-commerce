import { z } from "zod";

export const TaskAssetKindSchema = z.enum(["image", "video", "audio"]);

export const TaskAssetRoleSchema = z.enum([
  "product_front",
  "product_back",
  "product_detail",
  "model_reference",
  "scene_reference",
  "outfit_reference",
  "reference_video",
  "user_keyframe",
  "generated_result",
  "ai_keyframe",
  "image_variant",
  "video_clip",
  "final_video",
  "cover_image",
]);

export const TaskAssetSourceSchema = z.enum([
  "user_upload",
  "ai_generated",
  "external_url",
]);

export const TaskAssetUploadSchema = z
  .object({
    taskId: z.string().uuid(),
    assetKind: TaskAssetKindSchema,
    assetRole: TaskAssetRoleSchema,
    source: TaskAssetSourceSchema.default("user_upload"),
    url: z.string().url(),
    originalFilename: z.string().trim().min(1).max(255).optional(),
    mimeType: z.string().trim().min(1).max(128).optional(),
    metadataJson: z.record(z.unknown()).optional(),
  })
  .strict();

export const UpdateAssetRoleSchema = z
  .object({
    assetRole: TaskAssetRoleSchema,
  })
  .strict();

export type TaskAssetUploadInput = z.infer<typeof TaskAssetUploadSchema>;
export type UpdateAssetRoleInput = z.infer<typeof UpdateAssetRoleSchema>;
