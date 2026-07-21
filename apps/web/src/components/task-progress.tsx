"use client";

import { STATUS_LABELS } from "@/types/api";
import { cn } from "@/lib/utils";
import { Check, Loader2, XCircle } from "lucide-react";

const LEGACY_STAGES = [
  { key: "analyzing", label: "AI 分析" },
  { key: "plan_generated", label: "生成方案" },
  { key: "waiting_plan_selection", label: "等待选择" },
  { key: "script_generating", label: "脚本生成" },
  { key: "material_generating", label: "素材生成" },
  { key: "rendering", label: "渲染中" },
  { key: "completed", label: "完成" },
];

const FASHION_STAGES = [
  { key: "asset_uploading", label: "上传素材" },
  { key: "asset_analyzing", label: "AI 分析" },
  { key: "plan_generating", label: "生成方案" },
  { key: "storyboard_generating", label: "AI 分镜" },
  { key: "keyframe_configuring", label: "关键帧" },
  { key: "video_clip_generating", label: "视频片段" },
  { key: "rendering", label: "渲染审核" },
  { key: "completed", label: "完成" },
];

const FASHION_STAGE_ORDER = FASHION_STAGES.map((s) => s.key);
const LEGACY_STAGE_ORDER = LEGACY_STAGES.map((s) => s.key);

function resolveStageIndex(status: string, stages: typeof FASHION_STAGES): number {
  if (status === "failed" || status === "cancelled") return -2;
  const keys = stages.map((s) => s.key);

  // Exact match
  for (let i = keys.length - 1; i >= 0; i--) {
    if (status === keys[i]) return i;
  }

  // Fuzzy match — map intermediate statuses to their milestone group
  if (["waiting_asset_confirmation"].includes(status)) return 1; // AI 分析
  if (["waiting_plan_selection"].includes(status)) return 2; // 生成方案
  if (["waiting_storyboard_confirmation"].includes(status)) return 3; // AI 分镜
  if (["image_generating", "waiting_image_confirmation"].includes(status)) return 4; // 关键帧
  if (["waiting_video_clip_confirmation"].includes(status)) return 5; // 视频片段
  if (["waiting_final_review", "checking", "repairing"].includes(status)) return 6; // 渲染审核
  if (["completed", "exported"].includes(status)) return keys.length;

  // Fallback for legacy statuses
  if (status.startsWith("analysis")) return 0;
  if (status.startsWith("script")) return 3; // legacy: script_generating → 脚本生成
  if (status.startsWith("material")) return 4;
  if (status === "rendering" || status === "checking") return 5;

  return 0;
}

export function TaskProgress({
  status,
  errorMessage,
  taskMode,
}: {
  status: string;
  errorMessage?: string | null;
  taskMode?: string | null;
}) {
  const isFashion = taskMode && ["PRODUCT_CREATIVE", "REFERENCE_STORYBOARD", "USER_SCRIPT", "CUSTOM_STORYBOARD"].includes(taskMode);
  const stages = isFashion ? FASHION_STAGES : LEGACY_STAGES;
  const currentIdx = resolveStageIndex(status, stages);
  const isFailed = status === "failed";
  const isCancelled = status === "cancelled";

  return (
    <div className="space-y-4">
      {/* Progress bar */}
      <div className="relative">
        <div className="flex items-center justify-between">
          {stages.map((stage, i) => {
            const isCompleted = i < currentIdx;
            const isCurrent = i === currentIdx;
            const isUpcoming = i > currentIdx;

            return (
              <div key={stage.key} className="flex flex-1 flex-col items-center">
                {/* Connector line */}
                {i > 0 && (
                  <div
                    className={cn(
                      "absolute top-4 h-0.5 -translate-y-4",
                      isCompleted || isCurrent ? "bg-primary" : "bg-muted"
                    )}
                    style={{
                      left: `${((i - 1) / (stages.length - 1)) * 100}%`,
                      width: `${100 / (stages.length - 1)}%`,
                    }}
                  />
                )}
                {/* Icon */}
                <div
                  className={cn(
                    "relative z-10 flex h-8 w-8 items-center justify-center rounded-full border-2",
                    isCompleted && "border-primary bg-primary text-primary-foreground",
                    isCurrent && !isFailed && !isCancelled && "border-primary bg-background text-primary",
                    isUpcoming && "border-muted bg-background text-muted-foreground",
                    (isFailed || isCancelled) && "border-destructive bg-destructive/10 text-destructive"
                  )}
                >
                  {(isFailed || isCancelled) ? (
                    <XCircle className="h-4 w-4" />
                  ) : isCompleted ? (
                    <Check className="h-4 w-4" />
                  ) : isCurrent ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    i + 1
                  )}
                </div>
                <span
                  className={cn(
                    "mt-2 text-xs",
                    isCompleted || isCurrent ? "text-foreground font-medium" : "text-muted-foreground"
                  )}
                >
                  {stage.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Current status label */}
      <p className={cn("text-center text-sm", (isFailed || isCancelled) ? "text-destructive font-medium" : "text-muted-foreground")}>
        {isFailed ? "任务失败" : isCancelled ? "任务已取消" : STATUS_LABELS[status] || status}
      </p>

      {/* Error message */}
      {isFailed && errorMessage && (
        <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
          {errorMessage}
        </div>
      )}
    </div>
  );
}
