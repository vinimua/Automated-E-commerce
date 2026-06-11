// V1 API TypeScript types — placeholder
// Generated from 02-openapi-spec.yaml during Phase 3.
// Run: npx openapi-typescript ../../docs/02-openapi-spec.yaml -o ./src/types/api.generated.ts
//
// Application-level types that extend the generated API types:

import type { components } from "./api.generated";

export type VideoType = components["schemas"]["VideoType"];
export type VideoTaskStatus = components["schemas"]["VideoTaskStatus"];
export type VideoStatus = components["schemas"]["VideoStatus"];

export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

export type Product = components["schemas"]["Product"];
export type VideoTask = components["schemas"]["VideoTask"];
export type VideoPlan = components["schemas"]["VideoPlan"];
export type Storyboard = components["schemas"]["Storyboard"];
export type StoryboardShot = components["schemas"]["StoryboardShot"];
export type Video = components["schemas"]["Video"];
export type UserQuota = {
  videoQuota: number;
  imageQuota: number;
  videoClipQuota: number;
  exportQuota: number;
  usedVideoCount: number;
  usedImageCount: number;
  usedVideoClipCount: number;
  usedExportCount: number;
};
export type ErrorDetail = components["schemas"]["ErrorDetail"];

// V1 supported videoTypes for create UI
export const V1_VIDEO_TYPES: VideoType[] = [
  "pain_point_solution",
  "before_after",
  "review",
];
