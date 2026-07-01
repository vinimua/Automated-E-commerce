"use client";

import { apiRequest } from "@/lib/api-client";
import { TaskProgress } from "@/components/task-progress";
import { STATUS_LABELS, VIDEO_TYPE_LABELS } from "@/types/api";
import type { UserQuota, VideoTask } from "@/types/api";
import { PlusCircle, Video, ImageIcon, Download, AlertTriangle, CheckCircle } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

interface QuotaCardProps {
  label: string; used: number; total: number; icon: React.ReactNode;
}
function QuotaCard({ label, used, total, icon }: QuotaCardProps) {
  const pct = total > 0 ? Math.round((used / total) * 100) : 0;
  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">{label}</span>
        <span className="text-muted-foreground">{icon}</span>
      </div>
      <p className="mt-1 text-2xl font-semibold">{used}<span className="text-base font-normal text-muted-foreground">/{total}</span></p>
      <div className="mt-2 h-1.5 rounded-full bg-muted">
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${Math.min(pct, 100)}%` }} />
      </div>
    </div>
  );
}

const ACTIVE_STATES = [
  // V1
  "analyzing", "analysis_completed", "plan_generated", "script_generating", "script_generated",
  "material_generating", "material_generated", "rendering", "checking",
  // Fashion Creative Loop V1
  "asset_uploading", "asset_analyzing", "waiting_asset_confirmation", "reference_analyzing",
  "plan_generating", "storyboard_generating", "waiting_storyboard_confirmation",
  "keyframe_configuring", "image_generating", "waiting_image_confirmation",
  "video_clip_generating", "waiting_video_clip_confirmation",
  "waiting_final_review", "repairing",
];
const SPINNER_STATES = [
  "analyzing", "script_generating", "material_generating", "rendering",
  "asset_analyzing", "reference_analyzing", "plan_generating",
  "storyboard_generating", "image_generating", "video_clip_generating", "repairing",
];

function getTaskHref(task: VideoTask): string {
  const s = task.status;
  if (s === "failed" || s === "completed" || s === "exported" || s === "cancelled") {
    return `/video-tasks/${task.taskId}/progress`;
  }
  // ── Fashion Creative Loop V1 routing ──
  if (["asset_uploading", "asset_analyzing", "waiting_asset_confirmation"].includes(s)) {
    return `/video-tasks/${task.taskId}/assets`;
  }
  if (s === "reference_analyzing") {
    return `/video-tasks/${task.taskId}/reference-analysis`;
  }
  if (s === "waiting_plan_selection" || s === "plan_generating") {
    return `/video-tasks/${task.taskId}/plans`;
  }
  if (s === "storyboard_generating") {
    return `/video-tasks/${task.taskId}/progress`;
  }
  if (s === "waiting_storyboard_confirmation") {
    return `/video-tasks/${task.taskId}/storyboard`;
  }
  if (["keyframe_configuring", "image_generating", "waiting_image_confirmation"].includes(s)) {
    return `/video-tasks/${task.taskId}/keyframes`;
  }
  if (["video_clip_generating", "waiting_video_clip_confirmation"].includes(s)) {
    return `/video-tasks/${task.taskId}/clips`;
  }
  if (["waiting_final_review", "repairing"].includes(s)) {
    return `/video-tasks/${task.taskId}/review`;
  }
  // V1 legacy running states → progress page
  if (["script_generating", "script_generated", "material_generating", "material_generated", "rendering", "checking"].includes(s)) {
    return `/video-tasks/${task.taskId}/progress`;
  }
  // Fallback
  return `/video-tasks/${task.taskId}/plans`;
}

const NEEDS_ACTION_STATES = [
  "waiting_plan_selection", "waiting_asset_confirmation",
  "waiting_storyboard_confirmation", "waiting_image_confirmation",
  "waiting_video_clip_confirmation", "waiting_final_review",
];

function TaskCard({ task }: { task: VideoTask }) {
  const href = getTaskHref(task);
  const isWaiting = NEEDS_ACTION_STATES.includes(task.status);
  const isFailed = task.status === "failed";
  const isDone = task.status === "completed" || task.status === "exported";
  const showSpinner = SPINNER_STATES.includes(task.status);

  return (
    <Link href={href} className={`block rounded-lg border p-4 transition-colors hover:bg-accent/50 ${
      isWaiting ? "border-amber-500/50 bg-amber-50 hover:bg-amber-100/50" :
      isFailed ? "border-destructive/30 bg-destructive/5" :
      isDone ? "border-green-500/30 bg-green-50/50" :
      "bg-card"
    }`}>
      <div className="flex items-center justify-between">
        <div className="flex-1 min-w-0">
          <p className="font-medium truncate">
            {VIDEO_TYPE_LABELS[task.videoType] || task.videoType}
            <span className="ml-2 text-sm text-muted-foreground">{task.duration}s</span>
          </p>
          <div className="mt-0.5 flex items-center gap-2 text-sm">
            <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
              isDone ? "bg-green-100 text-green-700" :
              isFailed ? "bg-red-100 text-red-700" :
              isWaiting ? "bg-amber-100 text-amber-700" :
              "bg-blue-100 text-blue-700"
            }`}>
              {STATUS_LABELS[task.status] || task.status}
            </span>
            {isWaiting && (
              <span className="text-xs text-amber-600 font-medium">点击选择方案 →</span>
            )}
            {task.errorMessage && (
              <span className="truncate text-xs text-destructive">— {task.errorMessage}</span>
            )}
          </div>
        </div>
        <div className="ml-4 shrink-0 text-right">
          {isWaiting ? (
            <span className="inline-flex items-center rounded-full bg-amber-100 px-2.5 py-1 text-xs font-bold text-amber-700">
              待操作
            </span>
          ) : isDone ? (
            <span className="inline-flex items-center rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">
              ✓
            </span>
          ) : (
            <span className="text-xs text-muted-foreground">{task.progress}%</span>
          )}
        </div>
      </div>
      {showSpinner && (
        <div className="mt-3">
          <TaskProgress status={task.status} errorMessage={task.errorMessage} />
        </div>
      )}
    </Link>
  );
}

export default function DashboardPage() {
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const [tasks, setTasks] = useState<VideoTask[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const [quotaRes, taskRes] = await Promise.all([
          apiRequest<{ code: number; message: string; data: UserQuota }>("/api/quotas/me"),
          apiRequest<{ code: number; message: string; data: { items: VideoTask[]; total: number } }>("/api/video-tasks?pageSize=5"),
        ]);
        if (quotaRes.code === 0) setQuota(quotaRes.data);
        if (taskRes.code === 0) setTasks(taskRes.data.items || []);
      } catch (e) { /* silently ignore */ }
      finally { setLoading(false); }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  // Calculate usage stats
  const activeCount = tasks.filter((t) => !["completed", "failed", "exported"].includes(t.status)).length;
  const maxConcurrent = 2;
  const maxDaily = 10;
  const atConcurrentLimit = activeCount >= maxConcurrent;

  return (
    <div className="space-y-8 p-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">工作台</h1>
          <p className="text-sm text-muted-foreground">AI 驱动的 TikTok 带货视频生成</p>
        </div>
        <div className="flex items-center gap-4">
          {/* Limit status */}
          <div className="hidden sm:flex items-center gap-3 text-xs text-muted-foreground">
            <span className={atConcurrentLimit ? "text-destructive font-medium" : ""}>
              进行中: {activeCount}/{maxConcurrent}
            </span>
            <span className="text-border">|</span>
            <span>每日: {maxDaily}</span>
          </div>
          {atConcurrentLimit ? (
            <span className="inline-flex items-center gap-2 rounded-md bg-muted px-4 py-2 text-sm font-medium text-muted-foreground cursor-not-allowed">
              <AlertTriangle className="h-4 w-4" />
              并发已满
            </span>
          ) : (
            <Link
              href="/products/new"
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
            >
              <PlusCircle className="h-4 w-4" />
              新建视频
            </Link>
          )}
        </div>
      </div>

      {/* Usage warnings */}
      {atConcurrentLimit && (
        <div className="flex items-center gap-2 rounded-md border border-amber-500/50 bg-amber-50 p-3 text-sm text-amber-700">
          <AlertTriangle className="h-4 w-4 shrink-0" />
          你已达到并发任务上限（{maxConcurrent} 个），请等待当前任务完成或失败后再创建新任务。
        </div>
      )}

      {/* Quota */}
      <div className="grid grid-cols-4 gap-4">
        <QuotaCard label="视频生成" used={quota?.usedVideoCount ?? 0} total={quota?.videoQuota ?? 0} icon={<Video className="h-4 w-4" />} />
        <QuotaCard label="AI 图片" used={quota?.usedImageCount ?? 0} total={quota?.imageQuota ?? 0} icon={<ImageIcon className="h-4 w-4" />} />
        <QuotaCard label="AI 片段" used={quota?.usedVideoClipCount ?? 0} total={quota?.videoClipQuota ?? 0} icon={<Video className="h-4 w-4" />} />
        <QuotaCard label="导出" used={quota?.usedExportCount ?? 0} total={quota?.exportQuota ?? 0} icon={<Download className="h-4 w-4" />} />
      </div>

      {/* Recent tasks */}
      <div>
        <h2 className="mb-4 text-lg font-semibold">最近任务</h2>
        {tasks.length === 0 ? (
          <div className="rounded-lg border bg-card p-12 text-center">
            <p className="text-muted-foreground">还没有视频任务</p>
            <Link href="/products/new" className="mt-2 inline-block text-sm font-medium text-primary hover:underline">
              创建第一个 →
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {tasks.map((task) => (
              <TaskCard key={task.taskId} task={task} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
