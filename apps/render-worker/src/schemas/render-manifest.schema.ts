import { z } from "zod";

export const RenderManifestVersionSchema = z.literal("1.0.0");

export const RenderTemplateSchema = z.enum([
  "pain_point_solution_v1",
  "before_after_v1",
  "review_v1",
]);

export const RenderVideoTypeSchema = z.enum([
  "pain_point_solution",
  "before_after",
  "review",
  "product_showcase",
  "ugc_style",
  "tutorial",
]);

export const RenderAssetTypeSchema = z.enum(["image", "video", "product_image", "text"]);

export const RenderTransitionSchema = z.enum([
  "none",
  "quick_cut",
  "fade",
  "zoom_in",
  "slide_up",
  "slide_left",
  "flash",
]);

export const RenderZoomSchema = z.enum(["none", "slow_in", "slow_out", "pulse", "fast_in"]);
export const RenderPositionSchema = z.enum(["center", "top", "bottom", "left", "right"]);
export const RenderCropSchema = z.enum(["cover", "contain"]);
export const SubtitlePositionSchema = z.enum(["bottom_center", "middle_center", "top_center"]);
export const SubtitleBackgroundSchema = z.enum(["none", "semi_transparent", "solid"]);
export const MusicTypeSchema = z.enum(["default", "uploaded", "none"]);

const HttpUrlSchema = z
  .string()
  .url()
  .refine((value) => value.startsWith("http://") || value.startsWith("https://"), {
    message: "must be an absolute http(s) URI",
  });

export const EditConfigSchema = z
  .object({
    transition: RenderTransitionSchema,
    zoom: RenderZoomSchema,
    position: RenderPositionSchema,
    crop: RenderCropSchema.default("cover"),
  })
  .strict();

export const RenderAssetSchema = z
  .object({
    shotNo: z.number().int().min(1),
    type: RenderAssetTypeSchema,
    url: HttpUrlSchema.optional(),
    textContent: z.string().min(1).max(120).optional(),
    duration: z.number().int().min(1).max(8),
    subtitle: z.string().min(1).max(90),
    edit: EditConfigSchema,
  })
  .strict()
  .superRefine((asset, ctx) => {
    if (asset.type === "text") {
      if (!asset.textContent) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["textContent"],
          message: "textContent is required for text assets",
        });
      }
      return;
    }

    if (!asset.url) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["url"],
        message: `url is required for asset type '${asset.type}'`,
      });
    }
  });

export const SubtitleStyleSchema = z
  .object({
    fontSize: z.number().int().min(36).max(72),
    position: SubtitlePositionSchema,
    maxLines: z.union([z.literal(1), z.literal(2)]),
    background: SubtitleBackgroundSchema,
    safeAreaBottom: z.number().int().min(0).max(300).optional(),
  })
  .strict();

export const MusicConfigSchema = z
  .object({
    type: MusicTypeSchema,
    url: HttpUrlSchema.optional().nullable(),
    volume: z.number().min(0).max(1),
  })
  .strict();

export const VoiceoverConfigSchema = z
  .object({
    enabled: z.boolean(),
    url: HttpUrlSchema.optional().nullable(),
    volume: z.number().min(0).max(1),
  })
  .strict();

export const CoverConfigSchema = z
  .object({
    text: z.string().min(1).max(80),
    sourceShotNo: z.number().int().min(1),
  })
  .strict();

export const OutputConfigSchema = z
  .object({
    format: z.literal("mp4"),
    codec: z.literal("h264"),
    bitrate: z.string().regex(/^[1-9][0-9]*M$/),
  })
  .strict();

export const RenderManifestSchema = z
  .object({
    manifestVersion: RenderManifestVersionSchema,
    taskId: z.string().uuid(),
    videoId: z.string().uuid(),
    videoType: RenderVideoTypeSchema,
    template: RenderTemplateSchema,
    resolution: z.literal("1080x1920"),
    fps: z.literal(30),
    duration: z.number().int().min(15).max(30),
    assets: z.array(RenderAssetSchema).min(4).max(12),
    subtitleStyle: SubtitleStyleSchema,
    music: MusicConfigSchema,
    voiceover: VoiceoverConfigSchema.optional(),
    cover: CoverConfigSchema,
    output: OutputConfigSchema,
    metadata: z.record(z.unknown()).optional(),
  })
  .strict()
  .superRefine((manifest, ctx) => {
    const seenShots = new Set<number>();
    for (const [index, asset] of manifest.assets.entries()) {
      if (seenShots.has(asset.shotNo)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["assets", index, "shotNo"],
          message: `duplicate shotNo: ${asset.shotNo}`,
        });
      }
      seenShots.add(asset.shotNo);
    }

    const totalDuration = manifest.assets.reduce((total, asset) => total + asset.duration, 0);
    if (Math.abs(manifest.duration - totalDuration) > 1) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["duration"],
        message: `Duration mismatch: manifest=${manifest.duration}s, assets total=${totalDuration}s (max +/-1s)`,
      });
    }

    if (!seenShots.has(manifest.cover.sourceShotNo)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["cover", "sourceShotNo"],
        message: `cover.sourceShotNo ${manifest.cover.sourceShotNo} does not match any asset shotNo`,
      });
    }
  });

export type RenderManifestInput = z.infer<typeof RenderManifestSchema>;
