import { z } from "zod";

export const VideoTypeSchema = z.enum([
  "pain_point_solution",
  "before_after",
  "review",
  "product_showcase",
  "ugc_style",
  "tutorial",
]);

export const VideoTaskStatusSchema = z.enum([
  "draft",
  "asset_uploading",
  "asset_analyzing",
  "waiting_asset_confirmation",
  "reference_analyzing",
  "plan_generating",
  "analyzing",
  "analysis_completed",
  "plan_generated",
  "waiting_plan_selection",
  "storyboard_generating",
  "script_generating",
  "script_generated",
  "material_generating",
  "material_generated",
  "rendering",
  "checking",
  "completed",
  "failed",
  "exported",
  "waiting_storyboard_confirmation",
  "keyframe_configuring",
  "image_generating",
  "waiting_image_confirmation",
  "video_clip_generating",
  "waiting_video_clip_confirmation",
  "waiting_final_review",
  "repairing",
  "cancelled",
]);

export const TaskModeSchema = z.enum([
  "PRODUCT_CREATIVE",
  "REFERENCE_STORYBOARD",
  "USER_SCRIPT",
  "CUSTOM_STORYBOARD",
]);

export const CreateVideoTaskSchema = z
  .object({
    productId: z.string().uuid(),
    videoType: VideoTypeSchema,
    taskMode: TaskModeSchema.default("PRODUCT_CREATIVE"),
    productCategory: z.string().trim().min(1).optional(),
    shotCount: z.number().int().min(1).max(12).optional(),
    duration: z.number().int().min(15).max(30).optional(),
    language: z.string().trim().min(2).max(16).optional(),
    targetMarket: z.string().trim().min(2).max(16).optional(),
  })
  .strict();

export type CreateVideoTaskInput = z.infer<typeof CreateVideoTaskSchema>;

export const ConfirmPlanSchema = z
  .object({
    planId: z.string().uuid(),
  })
  .strict();

export type ConfirmPlanInput = z.infer<typeof ConfirmPlanSchema>;
