"use client";

import { STATUS_LABELS } from "@/types/api";
import { cn } from "@/lib/utils";
import { Check, Loader2, XCircle } from "lucide-react";

const PROGRESS_STAGES = [
  { key: "analyzing", label: "AI 分析" },
  { key: "plan_generated", label: "生成方案" },
  { key: "waiting_plan_selection", label: "等待选择" },
  { key: "script_generating", label: "脚本生成" },
  { key: "material_generating", label: "素材生成" },
  { key: "rendering", label: "渲染中" },
  { key: "completed", label: "完成" },
];

const STAGE_ORDER = PROGRESS_STAGES.map((s) => s.key);

function getCurrentStageIndex(status: string): number {
  if (status === "failed") return -2;
  if (status === "completed" || status === "exported") return STAGE_ORDER.length;
  // Find the furthest stage reached
  for (let i = STAGE_ORDER.length - 1; i >= 0; i--) {
    if (status === STAGE_ORDER[i]) return i;
  }
  // For intermediate statuses, map to nearest stage
  if (status.startsWith("analysis")) return 0;
  if (status.startsWith("script")) return 3;
  if (status.startsWith("material")) return 4;
  if (status === "rendering" || status === "checking") return 5;
  return 0;
}

export function TaskProgress({ status, errorMessage }: { status: string; errorMessage?: string | null }) {
  const currentIdx = getCurrentStageIndex(status);
  const isFailed = status === "failed";

  return (
    <div className="space-y-4">
      {/* Progress bar */}
      <div className="relative">
        <div className="flex items-center justify-between">
          {PROGRESS_STAGES.map((stage, i) => {
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
                    style={{ left: `${((i - 1) / (PROGRESS_STAGES.length - 1)) * 100}%`, width: `${100 / (PROGRESS_STAGES.length - 1)}%` }}
                  />
                )}
                {/* Icon */}
                <div
                  className={cn(
                    "relative z-10 flex h-8 w-8 items-center justify-center rounded-full border-2",
                    isCompleted && "border-primary bg-primary text-primary-foreground",
                    isCurrent && !isFailed && "border-primary bg-background text-primary",
                    isUpcoming && "border-muted bg-background text-muted-foreground",
                    isFailed && "border-destructive bg-destructive/10 text-destructive"
                  )}
                >
                  {isFailed ? (
                    <XCircle className="h-4 w-4" />
                  ) : isCompleted ? (
                    <Check className="h-4 w-4" />
                  ) : isCurrent ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    i + 1
                  )}
                </div>
                <span className={cn("mt-2 text-xs", (isCompleted || isCurrent) ? "text-foreground font-medium" : "text-muted-foreground")}>
                  {stage.label}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Current status label */}
      <p className={cn("text-center text-sm", isFailed ? "text-destructive font-medium" : "text-muted-foreground")}>
        {isFailed ? "任务失败" : STATUS_LABELS[status] || status}
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
