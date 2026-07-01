"use client";

import { apiRequest } from "@/lib/api-client";
import { TaskProgress } from "@/components/task-progress";
import { STATUS_LABELS, VIDEO_TYPE_LABELS } from "@/types/api";
import type { Video, VideoTask, VideoPlan } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { CancelTaskButton } from "@/components/cancel-task-button";

export default function VideoTaskPlansPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [plans, setPlans] = useState<VideoPlan[]>([]);
  const [videoId, setVideoId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [selecting, setSelecting] = useState(false);
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
      const [taskRes, plansRes] = await Promise.all([
        apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
        apiRequest<{ code: number; message: string; data: { plans: VideoPlan[] } }>(`/api/video-tasks/${id}/plans`).catch(() => ({ code: 0, message: "ok", data: { plans: [] } })),
      ]);
      if (taskRes.code === 0) {
        setTask(taskRes.data);
        if (["completed", "exported"].includes(taskRes.data.status)) {
          loadVideoId(taskRes.data);
        }
      }
      if (plansRes.code === 0 && plansRes.data?.plans) setPlans(plansRes.data.plans);
      setError("");
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setLoading(false);
    }
  }, [id, loadVideoId]);

  useEffect(() => {
    loadTask();
    const interval = setInterval(loadTask, 5000); // poll every 5s
    return () => clearInterval(interval);
  }, [loadTask]);

  async function selectPlan(planId: string) {
    setSelecting(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data: { status: string } }>(`/api/video-tasks/${id}/confirm-plan`, {
        method: "POST", body: { planId },
      });
      if (res.code === 0) {
        router.push(`/video-tasks/${id}/progress`);
      } else {
        setError(res.message || "选择失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setSelecting(false);
    }
  }

  if (loading) {
    return <div className="flex h-96 items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" /></div>;
  }
  if (!task) {
    return <div className="p-8"><p className="text-destructive">任务不存在</p></div>;
  }

  const isWaiting = task.status === "waiting_plan_selection";
  const isInProgress = ["analyzing", "analysis_completed", "plan_generated"].includes(task.status);
  const isPostSelection = ["storyboard_generating", "script_generating", "script_generated", "material_generating", "material_generated", "rendering", "checking"].includes(task.status);
  const isCompleted = task.status === "completed" || task.status === "exported";

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      {/* Header */}
      <div>
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">← 返回工作台</Link>
        <h1 className="mt-2 text-2xl font-bold">
          {VIDEO_TYPE_LABELS[task.videoType] || task.videoType}
          <span className="ml-2 text-base font-normal text-muted-foreground">{task.duration}s</span>
        </h1>
        <div className="mt-1 flex items-center gap-2">
          {task && !["completed", "exported", "failed", "cancelled"].includes(task.status) && (
            <CancelTaskButton taskId={id} />
          )}
          <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
            task.status === "failed" ? "bg-red-100 text-red-700" :
            task.status === "completed" ? "bg-green-100 text-green-700" :
            "bg-blue-100 text-blue-700"
          }`}>
            {STATUS_LABELS[task.status] || task.status}
          </span>
        </div>
      </div>

      {/* Progress */}
      <TaskProgress status={task.status} errorMessage={task.errorMessage} />

      {/* AI is working */}
      {isInProgress && (
        <div className="rounded-lg border bg-card p-12 text-center">
          <div className="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="mt-4 font-medium">AI 正在分析商品并生成视频方案...</p>
          <p className="mt-1 text-sm text-muted-foreground">预计需要 30-60 秒，页面会自动刷新</p>
        </div>
      )}

      {/* Plans ready — user selects */}
      {isWaiting && plans.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold">选择视频方案</h2>
          <p className="text-sm text-muted-foreground">AI 生成了 {plans.length} 个方案，请选择你最喜欢的一个</p>
          <div className="grid gap-4">
            {plans.map((plan) => (
              <div key={plan.planId} className="rounded-lg border bg-card p-6 space-y-3">
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <h3 className="font-semibold">{plan.title}</h3>
                    <p className="mt-1 text-sm text-muted-foreground">{plan.hook}</p>
                  </div>
                  {plan.score != null && (
                    <span className="ml-4 rounded-full bg-primary/10 px-3 py-1 text-sm font-semibold text-primary">
                      {plan.score}分
                    </span>
                  )}
                </div>
                {plan.structure && (
                  <div className="flex flex-wrap gap-1.5">
                    {plan.structure.split("→").map((s, i) => (
                      <span key={i} className="rounded-full bg-accent px-2 py-0.5 text-xs">{s.trim()}</span>
                    ))}
                  </div>
                )}
                {plan.reason && (
                  <p className="text-xs text-muted-foreground">💡 {plan.reason}</p>
                )}
                <button
                  onClick={() => selectPlan(plan.planId)}
                  disabled={selecting}
                  className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                >
                  {selecting ? "选择中..." : "选择此方案"}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {isWaiting && plans.length === 0 && (
        <div className="rounded-lg border bg-card p-8 text-center">
          <p className="font-medium">方案暂未返回</p>
          <p className="mt-1 text-sm text-muted-foreground">任务已进入方案选择阶段，但当前没有可选方案。页面会继续自动刷新。</p>
        </div>
      )}

      {/* Post-selection — AI working on script/storyboard/materials */}
      {isPostSelection && (
        <div className="rounded-lg border bg-card p-12 text-center">
          <div className="mx-auto h-12 w-12 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="mt-4 font-medium">AI 正在生成脚本、分镜和素材...</p>
          <p className="mt-1 text-sm text-muted-foreground">根据视频时长，预计需要 1-3 分钟</p>
        </div>
      )}

      {isCompleted && videoId && (
        <div className="rounded-lg border bg-card p-8 text-center">
          <p className="font-medium">任务已完成</p>
          <p className="mt-1 text-sm text-muted-foreground">视频已生成，点击下方查看</p>
          <Link
            href={`/videos/${videoId}`}
            className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
          >
            查看成片预览 →
          </Link>
        </div>
      )}

      {isCompleted && !videoId && (
        <div className="rounded-lg border bg-card p-8 text-center">
          <p className="font-medium">Video generated</p>
          <p className="mt-1 text-sm text-muted-foreground">Syncing final video information...</p>
        </div>
      )}

      {/* Failed */}
      {task.status === "failed" && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-6 text-center">
          <p className="font-medium text-destructive">任务失败</p>
          <p className="mt-1 text-sm text-muted-foreground">{task.errorMessage || "未知错误"}</p>
          {task.errorRetryable && (
            <button
              onClick={async () => {
                try {
                  await apiRequest(`/api/video-tasks/${id}/retry`, { method: "POST" });
                  loadTask();
                } catch (e: any) {
                  setError(e.message || "重试失败");
                }
              }}
              className="mt-4 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
            >
              重试
            </button>
          )}
        </div>
      )}

      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
