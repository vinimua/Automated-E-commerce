import { z } from "zod";

export const FeedbackCategorySchema = z.enum([
  "visual_quality",
  "product_accuracy",
  "lighting_issue",
  "action_stiffness",
  "missing_detail",
  "layout_composition",
  "style_mismatch",
  "other",
]);

export const RepairTargetTypeSchema = z.enum([
  "storyboard",
  "keyframe",
  "video_clip",
  "plan",
  "render_manifest",
  "final_video",
]);

export const RepairFeedbackSchema = z
  .object({
    taskId: z.string().uuid(),
    targetType: RepairTargetTypeSchema,
    targetId: z.string().uuid().optional(),
    feedbackCategory: FeedbackCategorySchema,
    affectedShots: z.array(z.number().int().min(1).max(12)).min(1),
    feedbackText: z.string().trim().min(1).max(2000),
    preserveProductConstraints: z.boolean().default(true),
  })
  .strict();

export const FinalReviewDecisionSchema = z
  .object({
    taskId: z.string().uuid(),
    decision: z.enum(["approve", "request_repair"]),
    feedback: z.string().trim().max(2000).optional(),
  })
  .strict()
  .superRefine((value, ctx) => {
    if (value.decision === "request_repair" && !value.feedback) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["feedback"],
        message: "feedback is required when decision is request_repair",
      });
    }
  });

export type RepairFeedbackInput = z.infer<typeof RepairFeedbackSchema>;
export type FinalReviewDecisionInput = z.infer<typeof FinalReviewDecisionSchema>;
