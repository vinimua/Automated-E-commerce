/**
 * Tencent COS Uploader — upload rendered MP4 + cover image.
 * Uses cos-nodejs-sdk-v5.
 *
 * Required env vars:
 *   COS_SECRET_ID  — Tencent Cloud SecretId
 *   COS_SECRET_KEY — Tencent Cloud SecretKey
 *   COS_REGION     — Bucket region (e.g. ap-guangzhou)
 *   COS_BUCKET     — Bucket name with APPID
 *   COS_CDN_DOMAIN — (optional) CDN domain for public URLs
 */
import COS from "cos-nodejs-sdk-v5";
import fs from "fs";
import { config } from "../config";

export interface UploadResult {
  videoUrl: string;
  coverUrl: string;
  fileSizeBytes: number;
}

let cosClient: COS | null = null;

function getCOSClient(): COS {
  if (!config.cos.secretId || !config.cos.secretKey || !config.cos.bucket) {
    throw new Error("COS is not configured: COS_SECRET_ID, COS_SECRET_KEY and COS_BUCKET are required");
  }
  if (!cosClient) {
    cosClient = new COS({
      SecretId: config.cos.secretId,
      SecretKey: config.cos.secretKey,
    });
  }
  return cosClient;
}

function buildCOSKey(taskId: string, renderTaskId: string, filename: string): string {
  return `tk-ai-video/${taskId}/${renderTaskId}/${filename}`;
}

function buildPublicUrl(cosKey: string): string {
  const { cdnDomain, bucket, region } = config.cos;
  if (cdnDomain) {
    return `https://${cdnDomain}/${cosKey}`;
  }
  return `https://${bucket}.cos.${region}.myqcloud.com/${cosKey}`;
}

async function uploadFile(
  key: string,
  filePath: string
): Promise<void> {
  const cos = getCOSClient();
  const { bucket, region } = config.cos;

  return new Promise((resolve, reject) => {
    cos.uploadFile(
      {
        Bucket: bucket,
        Region: region,
        Key: key,
        FilePath: filePath,
      },
      (err: any, data: any) => {
        if (err) {
          reject(err);
        } else {
          console.log(`[cos-uploader] Uploaded: ${key} (${data.Location || ""})`);
          resolve();
        }
      }
    );
  });
}

/**
 * Upload rendered video and cover to Tencent COS.
 * Returns public URLs for both files.
 */
export async function uploadToCOS(
  outputPath: string,
  coverPath: string,
  taskId: string,
  renderTaskId: string
): Promise<UploadResult> {
  console.log(`[cos-uploader] Uploading to COS... (bucket: ${config.cos.bucket})`);

  const videoKey = buildCOSKey(taskId, renderTaskId, "output.mp4");
  const coverKey = buildCOSKey(taskId, renderTaskId, "cover.jpg");

  await uploadFile(videoKey, outputPath);
  await uploadFile(coverKey, coverPath);

  const videoUrl = buildPublicUrl(videoKey);
  const coverUrl = buildPublicUrl(coverKey);

  let fileSizeBytes = 0;
  try {
    fileSizeBytes = fs.statSync(outputPath).size;
  } catch {
    // non-fatal: fileSize will be 0
  }

  console.log(`[cos-uploader] Complete: video=${videoUrl}, cover=${coverUrl}`);
  return { videoUrl, coverUrl, fileSizeBytes };
}
