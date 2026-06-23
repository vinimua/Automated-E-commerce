/**
 * Remotion-based video renderer — V1: 3 templates.
 *
 * Pipeline:
 *   1. Bundle the Remotion project (TSX → JS bundle)
 *   2. Select composition by template name
 *   3. Render to MP4 via @remotion/renderer
 *   4. Extract cover frame at sourceShotNo timecode
 */
import path from "path";
import fs from "fs";
import { bundle } from "@remotion/bundler";
import { selectComposition, renderMedia } from "@remotion/renderer";
import { config } from "../config";
import type { DownloadedAsset } from "./asset-downloader";

export interface RenderResult {
  outputPath: string;
  coverPath: string;
  duration: number;
  renderTimeMs: number;
}

/** Map manifest template field → Remotion Composition id */
const TEMPLATE_ID_MAP: Record<string, string> = {
  "pain_point_solution_v1": "PainPointSolutionV1",
  "before_after_v1": "BeforeAfterV1",
  "review_v1": "ReviewV1",
};

/**
 * Render a video from a RenderManifest using Remotion.
 *
 * @param manifest      The full RenderManifest object
 * @param taskId        Task ID for temp directory isolation
 * @param renderTaskId  Render task ID for temp directory isolation
 * @param downloadedAssets Local paths for downloaded/placeholder assets
 */
export async function renderVideo(
  manifest: Record<string, any>,
  taskId: string,
  renderTaskId: string,
  downloadedAssets: DownloadedAsset[]
): Promise<RenderResult> {
  const template = manifest.template || "pain_point_solution_v1";
  const compositionId = TEMPLATE_ID_MAP[template] || "PainPointSolutionV1";
  const duration = manifest.duration || 22;

  console.log(`[renderer] Rendering with template: ${template} (${compositionId})`);

  const tempDir = path.join(config.tempDir, taskId, renderTaskId);
  fs.mkdirSync(tempDir, { recursive: true });

  const outputPath = path.join(tempDir, "output.mp4");
  const coverPath = path.join(tempDir, "cover.jpg");

  const startTime = Date.now();

  // Step 1: Bundle the Remotion project
  console.log("[renderer] Bundling Remotion project...");
  const serveUrl = await bundle({
    entryPoint: path.resolve(process.cwd(), "src/remotion/root.tsx"),
    // webpackOverride: (currentConfig) => currentConfig  // can customize if needed
  });
  console.log(`[renderer] Bundle complete: ${serveUrl}`);

  // Step 2: Select composition
  const inputProps = {
    duration: manifest.duration,
    fps: manifest.fps || 30,
    assets: manifest.assets || [],
    subtitleStyle: manifest.subtitleStyle || {
      fontSize: 48,
      position: "bottom_center",
      maxLines: 1,
      background: "semi_transparent",
      safeAreaBottom: 180,
    },
    downloadedAssets,
  };

  const composition = await selectComposition({
    serveUrl,
    id: compositionId,
    inputProps,
  });

  console.log(
    `[renderer] Composition: ${composition.id} ${composition.width}x${composition.height} ` +
    `${composition.durationInFrames}frames @ ${composition.fps}fps`
  );

  // Step 3: Render to MP4
  console.log("[renderer] Rendering MP4...");
  await renderMedia({
    composition,
    serveUrl,
    codec: "h264",
    outputLocation: outputPath,
    inputProps,
    chromiumOptions: {
      disableWebSecurity: true,
    },
    onProgress: ({ progress, renderedFrames, encodedFrames }) => {
      if (renderedFrames % 30 === 0) {
        console.log(
          `[renderer] Progress: ${Math.round(progress * 100)}% ` +
          `(${renderedFrames}/${composition.durationInFrames} rendered, ${encodedFrames} encoded)`
        );
      }
    },
  });

  console.log(`[renderer] Render complete: ${outputPath}`);

  // Step 4: Extract cover frame from sourceShotNo
  const coverShotNo = manifest.cover?.sourceShotNo || 1;
  const coverTimeSec = getShotStartTime(manifest.assets || [], coverShotNo);
  await extractCoverWithSharp(outputPath, coverPath, coverTimeSec);

  const renderTimeMs = Date.now() - startTime;
  console.log(`[renderer] Total render time: ${renderTimeMs}ms`);

  return { outputPath, coverPath, duration, renderTimeMs };
}

/**
 * Calculate the start time in seconds for a given shot number.
 */
function getShotStartTime(
  assets: Array<{ shotNo: number; duration: number }>,
  shotNo: number
): number {
  let time = 0;
  for (const asset of assets) {
    if (asset.shotNo === shotNo) return time;
    time += asset.duration;
  }
  return time > 0 ? time / 2 : 0; // fallback: middle of first shot
}

/**
 * Extract a cover frame from an MP4 video using sharp (via FFmpeg piping).
 * Falls back to a simple solid-color image if sharp/FFmpeg fails.
 */
async function extractCoverWithSharp(
  videoPath: string,
  coverPath: string,
  timeSec: number
): Promise<void> {
  try {
    const { execSync } = require("child_process");
    // Use FFmpeg to extract a single frame as JPEG, pipe to sharp for resize
    const ffmpegCmd =
      `ffmpeg -ss ${timeSec} -i "${videoPath}" -vframes 1 -f image2pipe -vcodec mjpeg -q:v 3 -`;
    const frameBuffer = execSync(ffmpegCmd, { timeout: 30000, maxBuffer: 10 * 1024 * 1024 });
    fs.writeFileSync(coverPath, frameBuffer);
    console.log(`[renderer] Cover extracted: ${coverPath} (at ${timeSec}s)`);
  } catch (err: any) {
    console.warn(`[renderer] FFmpeg cover extraction failed: ${err.message}. Using fallback.`);
    // Fallback: create a blank cover
    try {
      const sharp = require("sharp");
      await sharp(Buffer.from(
        `<svg width="1080" height="1920">
          <rect width="1080" height="1920" fill="#1a1a1a" />
          <text x="540" y="960" fill="#ffffff" font-size="48" font-family="Arial"
                font-weight="bold" text-anchor="middle" dominant-baseline="central">
            Cover
          </text>
        </svg>`
      ))
        .jpeg({ quality: 80 })
        .toFile(coverPath);
    } catch {
      fs.writeFileSync(coverPath, Buffer.alloc(0));
    }
  }
}
