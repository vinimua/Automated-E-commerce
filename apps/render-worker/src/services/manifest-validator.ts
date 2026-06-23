/**
 * RenderManifest validation for the V1 renderer contract.
 *
 * This deliberately validates the contract fields used by the renderer instead
 * of accepting a partially shaped object. Asset download security and MIME
 * checks still happen in asset-downloader because they require network IO.
 */

const V1_TEMPLATES = new Set([
  "pain_point_solution_v1",
  "before_after_v1",
  "review_v1",
]);

const VIDEO_TYPES = new Set([
  "pain_point_solution",
  "before_after",
  "review",
  "product_showcase",
  "ugc_style",
  "tutorial",
]);

const ASSET_TYPES = new Set(["image", "video", "product_image", "text"]);
const TRANSITIONS = new Set([
  "none",
  "quick_cut",
  "fade",
  "zoom_in",
  "slide_up",
  "slide_left",
  "flash",
]);
const ZOOMS = new Set(["none", "slow_in", "slow_out", "pulse", "fast_in"]);
const POSITIONS = new Set(["center", "top", "bottom", "left", "right"]);
const CROPS = new Set(["cover", "contain"]);
const SUBTITLE_POSITIONS = new Set(["bottom_center", "middle_center", "top_center"]);
const SUBTITLE_BACKGROUNDS = new Set(["none", "semi_transparent", "solid"]);
const MUSIC_TYPES = new Set(["default", "uploaded", "none"]);

export interface ValidationResult {
  valid: boolean;
  error?: string;
  details?: Record<string, unknown>;
}

export async function validateRenderManifest(
  manifest: Record<string, unknown>
): Promise<ValidationResult> {
  if (!isRecord(manifest)) {
    return fail("manifest must be an object");
  }

  const required = [
    "manifestVersion",
    "taskId",
    "videoId",
    "videoType",
    "template",
    "resolution",
    "fps",
    "duration",
    "assets",
    "subtitleStyle",
    "music",
    "cover",
    "output",
  ];
  for (const field of required) {
    if (!(field in manifest)) return fail(`missing required field: ${field}`);
  }

  if (manifest.manifestVersion !== "1.0.0") {
    return fail("manifestVersion must be '1.0.0'");
  }
  if (!isUuid(manifest.taskId)) return fail("taskId must be a UUID");
  if (!isUuid(manifest.videoId)) return fail("videoId must be a UUID");
  if (!isEnum(manifest.videoType, VIDEO_TYPES)) return fail("unsupported videoType");
  if (!isEnum(manifest.template, V1_TEMPLATES)) return fail("unsupported template");
  if (manifest.resolution !== "1080x1920") return fail("resolution must be 1080x1920");
  if (manifest.fps !== 30) return fail("fps must be 30");
  if (!isIntegerInRange(manifest.duration, 15, 30)) {
    return fail("duration must be an integer between 15 and 30");
  }

  const assets = manifest.assets;
  if (!Array.isArray(assets)) return fail("assets must be an array");
  if (assets.length < 4 || assets.length > 12) {
    return fail(`assets length must be between 4 and 12, got ${assets.length}`);
  }

  let totalDuration = 0;
  const seenShots = new Set<number>();
  for (let i = 0; i < assets.length; i++) {
    const asset = assets[i];
    if (!isRecord(asset)) return fail(`assets[${i}] must be an object`);

    if (!isIntegerMin(asset.shotNo, 1)) return fail(`assets[${i}].shotNo must be >= 1`);
    if (seenShots.has(asset.shotNo)) return fail(`duplicate shotNo: ${asset.shotNo}`);
    seenShots.add(asset.shotNo);

    if (!isEnum(asset.type, ASSET_TYPES)) return fail(`assets[${i}].type is invalid`);
    if (!isIntegerInRange(asset.duration, 1, 8)) {
      return fail(`assets[${i}].duration must be between 1 and 8`);
    }
    totalDuration += asset.duration;

    if (!isStringInRange(asset.subtitle, 1, 90)) {
      return fail(`assets[${i}].subtitle must be 1-90 characters`);
    }

    if (asset.type === "text") {
      if (!isStringInRange(asset.textContent, 1, 120)) {
        return fail(`assets[${i}].textContent must be 1-120 characters`);
      }
    } else if (!isHttpUri(asset.url)) {
      return fail(`assets[${i}].url must be an absolute http(s) URI`);
    }

    const edit = asset.edit;
    if (!isRecord(edit)) return fail(`assets[${i}].edit must be an object`);
    if (!isEnum(edit.transition, TRANSITIONS)) return fail(`assets[${i}].edit.transition is invalid`);
    if (!isEnum(edit.zoom, ZOOMS)) return fail(`assets[${i}].edit.zoom is invalid`);
    if (!isEnum(edit.position, POSITIONS)) return fail(`assets[${i}].edit.position is invalid`);
    if ("crop" in edit && !isEnum(edit.crop, CROPS)) {
      return fail(`assets[${i}].edit.crop is invalid`);
    }
  }

  if (Math.abs((manifest.duration as number) - totalDuration) > 1) {
    return fail(
      `Duration mismatch: manifest=${manifest.duration}s, assets total=${totalDuration}s (max +/-1s)`
    );
  }

  const subtitleStyle = manifest.subtitleStyle;
  if (!isRecord(subtitleStyle)) return fail("subtitleStyle must be an object");
  if (!isIntegerInRange(subtitleStyle.fontSize, 36, 72)) {
    return fail("subtitleStyle.fontSize must be between 36 and 72");
  }
  if (!isEnum(subtitleStyle.position, SUBTITLE_POSITIONS)) {
    return fail("subtitleStyle.position is invalid");
  }
  if (!isIntegerInRange(subtitleStyle.maxLines, 1, 2)) {
    return fail("subtitleStyle.maxLines must be 1 or 2");
  }
  if (!isEnum(subtitleStyle.background, SUBTITLE_BACKGROUNDS)) {
    return fail("subtitleStyle.background is invalid");
  }
  if (
    "safeAreaBottom" in subtitleStyle &&
    !isIntegerInRange(subtitleStyle.safeAreaBottom, 0, 300)
  ) {
    return fail("subtitleStyle.safeAreaBottom must be between 0 and 300");
  }

  const music = manifest.music;
  if (!isRecord(music)) return fail("music must be an object");
  if (!isEnum(music.type, MUSIC_TYPES)) return fail("music.type is invalid");
  if (!isNumberInRange(music.volume, 0, 1)) return fail("music.volume must be between 0 and 1");
  if (music.url != null && !isHttpUri(music.url)) return fail("music.url must be an absolute URI");

  if ("voiceover" in manifest) {
    const voiceover = manifest.voiceover;
    if (!isRecord(voiceover)) return fail("voiceover must be an object");
    if (typeof voiceover.enabled !== "boolean") return fail("voiceover.enabled must be boolean");
    if (voiceover.url != null && !isHttpUri(voiceover.url)) {
      return fail("voiceover.url must be an absolute URI");
    }
    if (!isNumberInRange(voiceover.volume, 0, 1)) {
      return fail("voiceover.volume must be between 0 and 1");
    }
  }

  const cover = manifest.cover;
  if (!isRecord(cover)) return fail("cover must be an object");
  if (!isStringInRange(cover.text, 1, 80)) return fail("cover.text must be 1-80 characters");
  if (!isIntegerMin(cover.sourceShotNo, 1)) return fail("cover.sourceShotNo must be >= 1");
  if (!seenShots.has(cover.sourceShotNo)) {
    return fail(`cover.sourceShotNo ${cover.sourceShotNo} does not match any asset shotNo`);
  }

  const output = manifest.output;
  if (!isRecord(output)) return fail("output must be an object");
  if (output.format !== "mp4") return fail("output.format must be mp4");
  if (output.codec !== "h264") return fail("output.codec must be h264");
  if (typeof output.bitrate !== "string" || !/^[1-9][0-9]*M$/.test(output.bitrate)) {
    return fail("output.bitrate must match /^[1-9][0-9]*M$/");
  }

  return { valid: true };
}

function fail(error: string): ValidationResult {
  return { valid: false, error };
}

function isRecord(value: unknown): value is Record<string, any> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isEnum(value: unknown, allowed: Set<string>): value is string {
  return typeof value === "string" && allowed.has(value);
}

function isUuid(value: unknown): value is string {
  return (
    typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)
  );
}

function isIntegerInRange(value: unknown, min: number, max: number): value is number {
  return Number.isInteger(value) && (value as number) >= min && (value as number) <= max;
}

function isIntegerMin(value: unknown, min: number): value is number {
  return Number.isInteger(value) && (value as number) >= min;
}

function isNumberInRange(value: unknown, min: number, max: number): value is number {
  return typeof value === "number" && Number.isFinite(value) && value >= min && value <= max;
}

function isStringInRange(value: unknown, min: number, max: number): value is string {
  return typeof value === "string" && value.length >= min && value.length <= max;
}

function isHttpUri(value: unknown): value is string {
  if (typeof value !== "string") return false;
  try {
    const parsed = new URL(value);
    return parsed.protocol === "http:" || parsed.protocol === "https:";
  } catch {
    return false;
  }
}
