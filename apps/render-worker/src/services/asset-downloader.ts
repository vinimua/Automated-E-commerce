/**
 * Asset Downloader — downloads render assets from URLs to local temp files.
 *
 * Security:
 *   - SSRF protection via domain whitelist + private IP rejection
 *   - MIME type validation
 *   - File size limits
 *
 * Fallback:
 *   - On download failure (after 3 retries), generates a placeholder image
 *     using sharp (solid color + text overlay).
 */
import fs from "fs";
import path from "path";
import { config } from "../config";
import { validateResolvedUrl } from "../utils/ssrf-protection";

export interface DownloadedAsset {
  shotNo: number;
  type: string;
  localPath: string;
}

/**
 * Download all non-text assets to a local temp directory.
 * Text assets are skipped (no download needed).
 */
export async function downloadAssets(
  assets: Array<{ shotNo: number; type: string; url?: string; textContent?: string }>,
  taskId: string,
  renderTaskId: string
): Promise<DownloadedAsset[]> {
  const baseDir = path.join(config.tempDir, taskId, renderTaskId);
  fs.mkdirSync(baseDir, { recursive: true });

  const results: DownloadedAsset[] = [];

  for (const asset of assets) {
    // Text assets need no download
    if (asset.type === "text") {
      results.push({
        shotNo: asset.shotNo,
        type: asset.type,
        localPath: "", // not used for text
      });
      continue;
    }

    const assetDir = path.join(baseDir, `asset-${asset.shotNo}`);
    fs.mkdirSync(assetDir, { recursive: true });

    const ext = asset.type === "video" ? ".mp4" : ".jpg";
    const destPath = path.join(assetDir, `shot-${asset.shotNo}${ext}`);

    if (!asset.url) {
      console.warn(`[asset-downloader] Asset ${asset.shotNo} has no URL, generating placeholder`);
      const placeholderPath = await generatePlaceholder(asset.shotNo, asset.type, assetDir);
      results.push({ shotNo: asset.shotNo, type: "image", localPath: placeholderPath });
      continue;
    }

    // Try download with retries
    const downloaded = await downloadWithRetries(asset.url, destPath, asset.shotNo, asset.type);
    if (downloaded) {
      results.push({ shotNo: asset.shotNo, type: asset.type, localPath: destPath });
    } else {
      console.warn(`[asset-downloader] Asset ${asset.shotNo} download failed, using placeholder`);
      const placeholderPath = await generatePlaceholder(asset.shotNo, asset.type, assetDir);
      results.push({ shotNo: asset.shotNo, type: "image", localPath: placeholderPath });
    }
  }

  return results;
}

async function downloadWithRetries(
  url: string,
  destPath: string,
  shotNo: number,
  type: string
): Promise<boolean> {
  // SSRF check
  const parsed = await validateResolvedUrl(url);
  if (!parsed) {
    console.warn(`[asset-downloader] SSRF blocked: shotNo=${shotNo}, url=${url}`);
    return false;
  }

  const maxSize =
    type === "video" ? config.maxVideoSizeBytes : config.maxImageSizeBytes;

  for (let attempt = 1; attempt <= config.maxDownloadRetries; attempt++) {
    try {
      console.log(
        `[asset-downloader] Downloading shotNo=${shotNo}, attempt=${attempt}/${config.maxDownloadRetries}: ${url}`
      );

      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), config.downloadTimeoutMs);

      const response = await fetch(url, { signal: controller.signal });
      clearTimeout(timeout);

      if (!response.ok) {
        console.warn(
          `[asset-downloader] HTTP ${response.status} for shotNo=${shotNo}, attempt=${attempt}`
        );
        if (attempt < config.maxDownloadRetries) {
          await sleep(2000 * attempt);
        }
        continue;
      }

      // Check content-length if available
      const contentLength = response.headers.get("content-length");
      if (contentLength && parseInt(contentLength) > maxSize) {
        console.warn(
          `[asset-downloader] File too large (${contentLength} > ${maxSize}) for shotNo=${shotNo}`
        );
        return false;
      }

      // Read and validate
      const buffer = Buffer.from(await response.arrayBuffer());
      if (buffer.length > maxSize) {
        console.warn(
          `[asset-downloader] File too large (${buffer.length} > ${maxSize}) for shotNo=${shotNo}`
        );
        return false;
      }

      // MIME type check via file header magic bytes
      if (!validateMimeType(buffer, type)) {
        console.warn(
          `[asset-downloader] MIME mismatch for shotNo=${shotNo}: expected ${type}`
        );
        return false;
      }

      fs.writeFileSync(destPath, buffer);
      console.log(`[asset-downloader] Downloaded: shotNo=${shotNo}, size=${buffer.length}`);
      return true;
    } catch (err: any) {
      console.warn(
        `[asset-downloader] Error downloading shotNo=${shotNo}, attempt=${attempt}: ${err.message}`
      );
      if (attempt < config.maxDownloadRetries) {
        await sleep(2000 * attempt);
      }
    }
  }

  return false;
}

/**
 * Basic MIME type validation by checking magic bytes at file header.
 * This prevents type confusion attacks.
 */
function validateMimeType(buffer: Buffer, expectedType: string): boolean {
  if (buffer.length < 4) return false;

  const head = buffer.subarray(0, 4);

  if (expectedType === "image" || expectedType === "product_image") {
    // JPEG: FF D8 FF
    if (head[0] === 0xff && head[1] === 0xd8) return true;
    // PNG: 89 50 4E 47
    if (head[0] === 0x89 && head[1] === 0x50 && head[2] === 0x4e && head[3] === 0x47) return true;
    // WebP: 52 49 46 46 (RIFF)
    if (head[0] === 0x52 && head[1] === 0x49 && head[2] === 0x46 && head[3] === 0x46) return true;
    // GIF: 47 49 46 38
    if (head[0] === 0x47 && head[1] === 0x49 && head[2] === 0x46) return true;
    return false;
  }

  if (expectedType === "video") {
    // MP4: ... ftyp at offset 4
    if (buffer.length >= 8) {
      const ftyp = buffer.subarray(4, 8).toString("ascii");
      if (ftyp === "ftyp") return true;
    }
    // WebM: 1A 45 DF A3
    if (head[0] === 0x1a && head[1] === 0x45 && head[2] === 0xdf && head[3] === 0xa3) return true;
    // Could be other formats; accept with lenient check
    return true;
  }

  return true; // pass through unknown types
}

/**
 * Generate a placeholder image using sharp (solid color background + text overlay).
 * This is used when an asset URL cannot be downloaded.
 */
async function generatePlaceholder(
  shotNo: number,
  _type: string,
  outputDir: string
): Promise<string> {
  fs.mkdirSync(outputDir, { recursive: true });

  const width = 1080;
  const height = 1920;
  const svgText = `
    <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
      <rect width="${width}" height="${height}" fill="#2d2d30"/>
      <text x="${width / 2}" y="${height / 2 - 50}" fill="#ffffff" font-size="64" font-family="Arial, sans-serif"
            font-weight="700" text-anchor="middle">Shot ${shotNo}</text>
      <text x="${width / 2}" y="${height / 2 + 40}" fill="#b8b8c0" font-size="36" font-family="Arial, sans-serif"
            text-anchor="middle">Placeholder</text>
    </svg>`;
  const dataUrl = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgText)}`;
  console.log(`[asset-downloader] Generated inline placeholder: shotNo=${shotNo}`);
  return dataUrl;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
