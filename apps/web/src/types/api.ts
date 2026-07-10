/**
 * 🤖 AI AGENTS READ THIS:
 * All data structure types below MUST come from api.generated.ts (OpenAPI).
 * The ONLY things allowed in this file:
 *   1. Type re-exports from api.generated.ts
 *   2. Pure frontend constants (V1_VIDEO_TYPES, STATUS_LABELS, etc.)
 *   3. ApiResponse<T> / PageResponse<T> wrappers
 *
 * NEVER define data shapes inline (e.g. { code: number; data: Product }).
 * NEVER hand-write fields that exist in the OpenAPI spec.
 * If a type is missing, update docs/02-openapi-spec.yaml → run generate:api-types.
 */

import type { components } from "./api.generated";

type RequiredAuthData = {
  userId: string;
  accessToken: string;
  refreshToken: string;
};
type RequiredCreateProductData = { productId: string };
type RequiredCreateVideoTaskData = {
  taskId: string;
  status: VideoTaskStatus;
  progress: number;
};
type RequiredQuotaData = {
  videoQuota: number;
  imageQuota: number;
  videoClipQuota: number;
  exportQuota: number;
  usedVideoCount: number;
  usedImageCount: number;
  usedVideoClipCount: number;
  usedExportCount: number;
  quotaDate: string;
};

// Re-export schema types
export type VideoType = components["schemas"]["VideoType"];
export type VideoTaskStatus = components["schemas"]["VideoTaskStatus"];
export type VideoStatus = components["schemas"]["VideoStatus"];
export type TaskMode = components["schemas"]["TaskMode"];
export type TaskAsset = components["schemas"]["TaskAsset"] & {
  assetId: string;
  taskId: string;
  assetKind: "image" | "video" | "audio";
  url: string;
  assetRole: string;
  source: "user_upload" | "ai_generated" | "external_url";
  confirmed: boolean;
  createdAt: string;
};
export type Product = components["schemas"]["Product"] & {
  id: string;
  name: string;
  targetMarket: string;
  language: string;
};
export type CreateProductRequest = components["schemas"]["CreateProductRequest"];
export type CreateProductData = RequiredCreateProductData;
export type CreateProductResponse = components["schemas"]["CreateProductResponse"];
export type UpdateProductRequest = components["schemas"]["UpdateProductRequest"];
export type VideoTask = components["schemas"]["VideoTask"] & {
  taskId: string;
  productId: string;
  status: VideoTaskStatus;
  progress: number;
  duration: number;
  videoType: VideoType;
  needSubtitles: boolean;
  needVoiceover: boolean;
  retryCount: number;
  taskMode: TaskMode;
};
export type CreateVideoTaskRequest = components["schemas"]["CreateVideoTaskRequest"];
export type CreateVideoTaskData = RequiredCreateVideoTaskData;
export type CreateVideoTaskResponse = components["schemas"]["CreateVideoTaskResponse"];
export type VideoPlan = components["schemas"]["VideoPlan"] & {
  planId: string;
  type: VideoType;
  title: string;
  hook: string;
};
export type VideoPlanListData = NonNullable<components["schemas"]["VideoPlanListResponse"]["data"]>;
export type StoryboardShot = components["schemas"]["StoryboardShot"];
export type Storyboard = components["schemas"]["Storyboard"];
export type UpdateStoryboardRequest = components["schemas"]["UpdateStoryboardRequest"];
export type Video = components["schemas"]["Video"];
export type VideoExportResponse = components["schemas"]["VideoExportResponse"];
export type UserQuota = RequiredQuotaData;
export type PresignedUploadRequest = components["schemas"]["PresignedUploadRequest"];
export type PresignedUploadResponse = components["schemas"]["PresignedUploadResponse"];
export type PageMeta = components["schemas"]["PageMeta"];
export type ErrorDetail = components["schemas"]["ErrorDetail"];
export type AuthData = RequiredAuthData;

export type UserItem = components["schemas"]["UserItem"];

// Admin types
export type AdminUserListData = {
  items: UserItem[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};
export type AdminVideoListData = {
  items: Video[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};
export type AdminQuotaItem = components["schemas"]["AdminQuotaItem"];
export type AdminQuotaUpdateRequest = components["schemas"]["AdminQuotaUpdateRequest"];
export type AdminQuotaListData = {
  items: AdminQuotaItem[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
};
export type AdminLogItem = Record<string, unknown>;
export type AdminLogListData = {
  items: AdminLogItem[];
  total: number;
};

// Wrapped API response envelope
export type ApiResponse<T> = { code: number; message: string; data: T };
export type PageResponse<T> = { code: number; message: string; data: { items: T[] } & PageMeta };

// Internal/legacy structure hints. Do not use this list as the creation entry.
// Fashion Creative Loop V1 starts from taskMode; videoType is only an AI/rendering hint.
export const V1_VIDEO_TYPES: VideoType[] = ["pain_point_solution", "before_after", "review"];
export const V1_DURATIONS = [15, 20, 25, 30] as const;
export const PRODUCT_IMAGE_FOLDERS = ["product-images"] as const;

// Status display helpers
export const STATUS_LABELS: Record<string, string> = {
  draft: "草稿", analyzing: "AI 分析中", analysis_completed: "分析完成",
  plan_generated: "方案已生成", waiting_plan_selection: "等待选择方案",
  script_generating: "生成脚本中", script_generated: "脚本已生成",
  material_generating: "生成素材中", material_generated: "素材已生成",
  rendering: "渲染中", checking: "质检中", completed: "已完成",
  failed: "失败", exported: "已导出",
  // Fashion Creative Loop V1
  asset_uploading: "上传素材", asset_analyzing: "AI 分析素材中",
  waiting_asset_confirmation: "等待确认素材", reference_analyzing: "AI 分析参考视频中",
  plan_generating: "AI 生成方案中", storyboard_generating: "AI 生成分镜中",
  waiting_storyboard_confirmation: "等待确认分镜",
  keyframe_configuring: "配置关键帧", image_generating: "AI 生成关键帧中",
  waiting_image_confirmation: "等待确认关键帧",
  video_clip_generating: "AI 生成片段中", waiting_video_clip_confirmation: "等待确认片段",
  waiting_final_review: "等待最终审核", repairing: "修复中", cancelled: "已取消",
};

export const VIDEO_TYPE_LABELS: Record<string, string> = {
  pain_point_solution: "痛点解决方案",
  before_after: "前后对比",
  review: "产品测评",
  product_showcase: "商品展示",
  ugc_style: "UGC 风格",
  tutorial: "教程演示",
};

export const TASK_MODE_LABELS: Record<string, string> = {
  PRODUCT_CREATIVE: "商品创意",
  REFERENCE_STORYBOARD: "参考视频",
  USER_SCRIPT: "用户脚本",
  CUSTOM_STORYBOARD: "用户分镜",
};
