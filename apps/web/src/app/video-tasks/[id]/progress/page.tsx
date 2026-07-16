"use client";

import { apiRequest } from "@/lib/api-client";
import { TaskProgress } from "@/components/task-progress";
import { STATUS_LABELS, TASK_MODE_LABELS, VIDEO_TYPE_LABELS } from "@/types/api";
import type { Video, VideoTask } from "@/types/api";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, RefreshCw } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

export default function TaskProgressPage() {
  const { id } = useParams<{ id: string }>();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [videoId, setVideoId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [retrying, setRetrying] = useState(false);
  const [error, setError] = useState("");

  const loadVideoId = useCallback(async (currentTask: VideoTask) => {
    if (!currentTask.productId) return;
    try {
      const params = new URLSearchParams({
        productId: currentTask.productId,
        page: "1",
        pageSize: "20",
      });
      const res = await apiRequest<{
        code: number;
        message: string;
        data: { items: Video[] };
      }>(`/api/videos?${params.toString()}`);
      const matchedVideo = res.data?.items?.find((video) => video.taskId === currentTask.taskId);
      setVideoId(matchedVideo?.videoId || null);
    } catch {
      setVideoId(null);
    }
  }, []);

  const loadTask = useCallback(async () => {
    try {
      const res = await apiRequest<{ code: number; message: string; data: VideoTask }>(
        `/api/video-tasks/${id}`
      );
      if (res.code === 0) {
        setTask(res.data);
        if (["completed", "exported"].includes(res.data.status)) {
          loadVideoId(res.data);
        }
        setError("");
      } else {
        setError(res.message || "加载失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setLoading(false);
    }
  }, [id, loadVideoId]);

  useEffect(() => {
    loadTask();
    // Poll until terminal state
    const interval = setInterval(() => {
      setTask((prev) => {
        if (prev && ["completed", "exported", "failed"].includes(prev.status)) {
          clearInterval(interval);
          return prev;
        }
        loadTask();
        return prev;
      });
    }, 5000);
    return () => clearInterval(interval);
  }, [loadTask]);

  async function handleRetry() {
    setRetrying(true);
    try {
      const res = await apiRequest<{ code: number; message: string }>(
        `/api/video-tasks/${id}/retry`,
        { method: "POST" }
      );
      if (res.code === 0) {
        loadTask();
      } else {
        setError(res.message || "重试失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setRetrying(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!task) {
    return (
      <div className="p-8">
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
          ← 返回工作台
        </Link>
        <p className="mt-4 text-destructive">任务不存在</p>
      </div>
    );
  }

  const isCompleted = task.status === "completed" || task.status === "exported";
  const isFailed = task.status === "failed";
  const isTerminal = isCompleted || isFailed;
  const isFashionAssetStage = ["asset_uploading", "asset_analyzing", "waiting_asset_confirmation"].includes(task.status);
  const isFashionKeyframeStage = ["keyframe_configuring", "image_generating", "waiting_image_confirmation"].includes(task.status);
  const isFashionClipStage = ["video_clip_generating", "waiting_video_clip_confirmation"].includes(task.status);
  const isFashionReviewStage = ["waiting_final_review", "repairing", "rendering"].includes(task.status);
  const canViewStoryboard = [
    "waiting_storyboard_confirmation",
    "keyframe_configuring",
    "image_generating",
    "waiting_image_confirmation",
    "video_clip_generating",
    "waiting_video_clip_confirmation",
    "rendering",
    "waiting_final_review",
    "completed",
    "exported",
  ].includes(task.status);

  return (
    <div className="mx-auto max-w-3xl space-y-8 p-8">
      {/* Header */}
      <div>
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
          ← 返回工作台
        </Link>
        <h1 className="mt-2 text-2xl font-bold">
          {task.selectedPlanTitle || TASK_MODE_LABELS[task.taskMode] || "创作任务"}
          <span className="ml-2 text-base font-normal text-muted-foreground">{task.duration}s</span>
        </h1>
        {task.videoType && (
          <p className="mt-1 text-sm text-muted-foreground">
            {VIDEO_TYPE_LABELS[task.videoType] || task.videoType}
          </p>
        )}
        <div className="mt-1 flex items-center gap-2">
          <span
            className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
              isFailed
                ? "bg-red-100 text-red-700"
                : isCompleted
                ? "bg-green-100 text-green-700"
                : "bg-blue-100 text-blue-700"
            }`}
          >
            {STATUS_LABELS[task.status] || task.status}
          </span>
          <span className="text-sm text-muted-foreground">{task.progress}%</span>
        </div>
        {!isTerminal && <CancelTaskButton taskId={id} />}
      </div>

      {/* Progress bar */}
      <TaskProgress status={task.status} errorMessage={task.errorMessage} taskMode={task.taskMode} />

      {/* Action links based on state */}
      <div className="space-y-4">
        {canViewStoryboard && (
          <Link
            href={`/video-tasks/${id}/storyboard`}
            className="block rounded-lg border bg-card p-4 text-center text-sm font-medium text-primary hover:bg-accent transition-colors"
          >
            查看分镜脚本 →
          </Link>
        )}

        {/* Fashion Creative Loop V1 action links */}
        {["plan_generating", "waiting_plan_selection"].includes(task.status) && (
          <Link
            href={`/video-tasks/${id}/plans`}
            className="block rounded-lg border border-amber-500/50 bg-amber-50 p-4 text-center text-sm font-medium text-amber-700 hover:bg-amber-100 transition-colors"
          >
            📋 选择视频方案 →
          </Link>
        )}
        {isFashionAssetStage && (
          <Link
            href={`/video-tasks/${id}/assets`}
            className="block rounded-lg border border-blue-500/50 bg-blue-50 p-4 text-center text-sm font-medium text-blue-700 hover:bg-blue-100 transition-colors"
          >
            📷 管理素材 →
          </Link>
        )}
        {isFashionKeyframeStage && (
          <Link
            href={`/video-tasks/${id}/keyframes`}
            className="block rounded-lg border border-purple-500/50 bg-purple-50 p-4 text-center text-sm font-medium text-purple-700 hover:bg-purple-100 transition-colors"
          >
            🖼️ 管理关键帧 →
          </Link>
        )}
        {isFashionClipStage && (
          <Link
            href={`/video-tasks/${id}/clips`}
            className="block rounded-lg border border-orange-500/50 bg-orange-50 p-4 text-center text-sm font-medium text-orange-700 hover:bg-orange-100 transition-colors"
          >
            🎬 管理视频片段 →
          </Link>
        )}
        {isFashionReviewStage && (
          <Link
            href={`/video-tasks/${id}/review`}
            className="block rounded-lg border border-green-500/50 bg-green-50 p-4 text-center text-sm font-medium text-green-700 hover:bg-green-100 transition-colors"
          >
            ✅ 查看成片审核 →
          </Link>
        )}

        {isCompleted && videoId && (
          <Link
            href={`/videos/${videoId}`}
            className="block rounded-lg border border-primary/50 bg-primary/5 p-6 text-center transition-colors hover:bg-primary/10"
          >
            <p className="text-lg font-semibold text-primary">🎬 视频生成完成</p>
            <p className="mt-1 text-sm text-muted-foreground">点击查看成片预览和下载</p>
          </Link>
        )}

        {isCompleted && !videoId && (
          <div className="rounded-lg border border-primary/50 bg-primary/5 p-6 text-center">
            <p className="text-lg font-semibold text-primary">Video generated</p>
            <p className="mt-1 text-sm text-muted-foreground">Syncing final video information...</p>
          </div>
        )}

        {isFailed && (
          <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-6 text-center">
            <p className="font-medium text-destructive">任务失败</p>
            {task.errorMessage && (
              <p className="mt-1 text-sm text-muted-foreground">{task.errorMessage}</p>
            )}
            {task.errorRetryable !== false && (
              <button
                onClick={handleRetry}
                disabled={retrying}
                className="mt-4 inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              >
                <RefreshCw className={`h-4 w-4 ${retrying ? "animate-spin" : ""}`} />
                {retrying ? "重试中..." : "重新尝试"}
              </button>
            )}
          </div>
        )}
      </div>

      {/* Task detail info */}
      <div className="rounded-lg border bg-card p-6">
        <h3 className="text-sm font-semibold">任务详情</h3>
        <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
          <div>
            <dt className="text-muted-foreground">任务 ID</dt>
            <dd className="font-mono text-xs">{task.taskId}</dd>
          </div>
          <div>
            <dt className="text-muted-foreground">重试次数</dt>
            <dd>{task.retryCount ?? 0}</dd>
          </div>
          {task.failedStage && (
            <div className="col-span-2">
              <dt className="text-muted-foreground">失败阶段</dt>
              <dd>{task.failedStage}</dd>
            </div>
          )}
          {task.errorCode && (
            <div className="col-span-2">
              <dt className="text-muted-foreground">错误码</dt>
              <dd className="font-mono text-xs">{task.errorCode}</dd>
            </div>
          )}
        </dl>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
