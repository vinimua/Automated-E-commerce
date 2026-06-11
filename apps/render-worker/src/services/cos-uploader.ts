/**
 * 腾讯云 COS Uploader — 上传渲染成品 MP4 + 封面图。
 * 使用 cos-nodejs-sdk-v5。
 * Full implementation in Phase 5.
 *
 * 环境变量:
 *   COS_SECRET_ID  — 腾讯云 SecretId
 *   COS_SECRET_KEY — 腾讯云 SecretKey
 *   COS_REGION     — 存储桶地域 (e.g. ap-guangzhou)
 *   COS_BUCKET     — 存储桶名称 (含 APPID)
 *   COS_CDN_DOMAIN — (可选) CDN 加速域名
 */

import path from "path";
import fs from "fs";

export interface UploadResult {
  videoUrl: string;
  coverUrl: string;
  fileSizeBytes: number;
}

function getCOSClient(): any {
  // TODO: Phase 5 — initialize Tencent COS SDK client
  // const COS = require("cos-nodejs-sdk-v5");
  // return new COS({
  //   SecretId: process.env.COS_SECRET_ID,
  //   SecretKey: process.env.COS_SECRET_KEY,
  // });
  return null;
}

function buildCOSKey(taskId: string, renderTaskId: string, filename: string): string {
  const cos = getCOSClient();
  // COS object key 格式: tk-ai-video/{taskId}/{renderTaskId}/{filename}
  return `tk-ai-video/${taskId}/${renderTaskId}/${filename}`;
}

function buildPublicUrl(cosKey: string): string {
  const cdn = process.env.COS_CDN_DOMAIN;
  const bucket = process.env.COS_BUCKET;
  const region = process.env.COS_REGION;
  if (cdn) {
    return `https://${cdn}/${cosKey}`;
  }
  return `https://${bucket}.cos.${region}.myqcloud.com/${cosKey}`;
}

export async function uploadToCOS(
  renderResult: { outputPath: string; coverPath: string },
  taskId: string,
  renderTaskId: string
): Promise<UploadResult> {
  console.log(`[cos-uploader] Uploading to Tencent COS... (bucket: ${process.env.COS_BUCKET})`);

  // TODO: Phase 5 — actual upload using cos-nodejs-sdk-v5
  // const cos = getCOSClient();
  // const videoKey = buildCOSKey(taskId, renderTaskId, "output.mp4");
  // const coverKey = buildCOSKey(taskId, renderTaskId, "cover.jpg");
  //
  // await cos.uploadFile({ Bucket, Region, Key: videoKey, FilePath: renderResult.outputPath });
  // await cos.uploadFile({ Bucket, Region, Key: coverKey, FilePath: renderResult.coverPath });

  const videoKey = buildCOSKey(taskId, renderTaskId, "output.mp4");
  const coverKey = buildCOSKey(taskId, renderTaskId, "cover.jpg");

  const videoUrl = buildPublicUrl(videoKey);
  const coverUrl = buildPublicUrl(coverKey);

  return {
    videoUrl,
    coverUrl,
    fileSizeBytes: 0, // Phase 5: fs.statSync(renderResult.outputPath).size
  };
}
