"use client";

import { CancelTaskButton } from "@/components/cancel-task-button";
import { apiRequest } from "@/lib/api-client";
import type { Video, VideoTask } from "@/types/api";
import { AlertCircle, ArrowLeft, CheckCircle2, Clock, Loader2, Wrench } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

interface RepairEventItem {
  repairEventId: string;
  taskId: string;
  targetType: string;
  targetId?: string | null;
  userFeedback: string;
  issueType?: string | null;
  status: string;
  createdAt: string;
  updatedAt?: string | null;
}

const TARGET_TYPE_LABELS: Record<string, string> = {
  storyboard: "分镜",
  keyframe: "关键帧",
  video_clip: "视频片段",
  render_manifest: "渲染清单",
  final_video: "最终成片",
  plan: "创意方案",
};

const FEEDBACK_CATEGORIES = [
  { value: "visual_quality", label: "画质问题" },
  { value: "product_accuracy", label: "商品不准确" },
  { value: "lighting_issue", label: "光线问题" },
  { value: "action_stiffness", label: "动作生硬" },
  { value: "missing_detail", label: "细节缺失" },
  { value: "layout_composition", label: "构图问题" },
  { value: "style_mismatch", label: "风格不匹配" },
  { value: "other", label: "其他" },
];

function statusText(status?: string) {
  switch (status) {
    case "rendering":
      return "正在合成最终视频";
    case "waiting_final_review":
      return "请审核最终成片";
    case "repairing":
      return "AI 正在根据反馈修复";
    case "completed":
    case "exported":
      return "成片已确认";
    case "failed":
      return "任务失败";
    default:
      return "等待成片结果";
  }
}

function eventStatusLabel(status: string) {
  switch (status) {
    case "completed":
      return "已完成";
    case "failed":
      return "失败";
    case "in_progress":
      return "进行中";
    case "pending":
      return "待处理";
    default:
      return status;
  }
}

export default function ReviewPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [video, setVideo] = useState<Video | null>(null);
  const [repairEvents, setRepairEvents] = useState<RepairEventItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [showFeedback, setShowFeedback] = useState(false);
  const [feedbackCategory, setFeedbackCategory] = useState("visual_quality");
  const [feedbackTarget, setFeedbackTarget] = useState("final_video");
  const [feedbackText, setFeedbackText] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const loadTaskVideo = useCallback(async (currentTask: VideoTask) => {
    if (!currentTask.productId) {
      setVideo(null);
      return;
    }

    if (!["rendering", "waiting_final_review", "repairing", "completed", "exported"].includes(currentTask.status)) {
      setVideo(null);
      return;
    }

    try {
      const vRes = await apiRequest<{ code: number; message: string; data: { items?: Video[] } }>(
        `/api/videos?productId=${currentTask.productId}`
      );
      const matched = vRes.data?.items?.find((item) => item.taskId === id) || null;
      setVideo(matched);
    } catch {
      setVideo(null);
    }
  }, [id]);

  const load = useCallback(async () => {
    try {
      const [taskRes, repairRes] = await Promise.all([
        apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
        apiRequest<{ code: number; message: string; data: { taskId: string; events: RepairEventItem[] } }>(
          `/api/video-tasks/${id}/repair-events`
        ),
      ]);

      if (taskRes.code === 0 && taskRes.data) {
        setTask(taskRes.data);
        await loadTaskVideo(taskRes.data);
      }

      if (repairRes.code === 0 && repairRes.data) {
        setRepairEvents(repairRes.data.events || []);
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "加载最终审核页失败");
    } finally {
      setLoading(false);
    }
  }, [id, loadTaskVideo]);

  useEffect(() => {
    load();
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
  }, [load]);

  async function handleSubmitFeedback() {
    if (!feedbackText.trim()) return;
    setSubmitting(true);
    setError("");

    try {
      await apiRequest(`/api/video-tasks/${id}/feedback`, {
        method: "POST",
        body: {
          feedbackText: feedbackText.trim(),
          category: feedbackCategory,
          targetType: feedbackTarget,
        },
      });
      setShowFeedback(false);
      setFeedbackText("");
      await load();
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "提交反馈失败");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleApprove() {
    setSubmitting(true);
    setError("");

    try {
      const res = await apiRequest<{ code: number; message: string; data?: { status?: string } }>(
        `/api/video-tasks/${id}/approve`,
        { method: "POST" }
      );

      if (res.code !== 0) {
        setError(res.message || "确认成片失败");
        return;
      }

      if (video?.videoId) {
        router.push(`/videos/${video.videoId}`);
      } else {
        await load();
      }
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : "确认成片失败");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
      </div>
    );
  }

  const isRendering = task?.status === "rendering";
  const isReviewing = task?.status === "waiting_final_review";
  const isRepairing = task?.status === "repairing";
  const isCompleted = task?.status === "completed" || task?.status === "exported";
  const canShowVideo = Boolean(video?.videoUrl) && (isReviewing || isCompleted || isRepairing);

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <header>
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="mr-1 inline h-3 w-3" />
          返回工作台
        </Link>
        <h1 className="mt-2 text-2xl font-bold">成片审核</h1>
        <p className="text-sm text-muted-foreground">{statusText(task?.status)}</p>
        {!isCompleted && (
          <div className="mt-3">
            <CancelTaskButton taskId={id} />
          </div>
        )}
      </header>

      {error && (
        <div className="flex items-start gap-2 rounded-md bg-destructive/10 p-3 text-sm text-destructive">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {isRendering && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12 text-center">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
          <p className="text-lg font-medium">正在渲染成片</p>
          <p className="text-sm text-muted-foreground">
            系统正在把已确认的视频片段合成为最终视频。渲染完成后，这里会自动显示预览。
          </p>
        </div>
      )}

      {isRepairing && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12 text-center">
          <Wrench className="h-10 w-10 animate-pulse text-amber-500" />
          <p className="text-lg font-medium">修复进行中</p>
          <p className="text-sm text-muted-foreground">AI 正在根据你的反馈重新处理成片。</p>
        </div>
      )}

      {!isRendering && !canShowVideo && (isReviewing || isCompleted) && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-800">
          成片记录尚未写入，可能是渲染回调还在路上。请稍后刷新；如果长时间没有结果，需要检查 render worker
          是否成功回调 Java API。
        </div>
      )}

      {canShowVideo && (
        <section className="overflow-hidden rounded-lg border bg-card">
          <video src={video?.videoUrl} controls className="w-full bg-black" style={{ maxHeight: 520 }} />
          <div className="flex items-center justify-between gap-4 p-4">
            <div>
              <p className="font-medium">{video?.title || "生成视频"}</p>
              <p className="text-sm text-muted-foreground">
                {video?.duration ? `${video.duration}s` : "时长待写入"} · {video?.resolution || "1080x1920"}
                {video?.qualityScore != null ? ` · 质量分 ${video.qualityScore}` : ""}
              </p>
            </div>
            {video?.videoUrl && (
              <a
                href={video.videoUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="shrink-0 text-sm font-medium text-primary hover:underline"
              >
                打开视频
              </a>
            )}
          </div>
        </section>
      )}

      {isReviewing && (
        <section className="flex flex-wrap items-center gap-4">
          <button
            onClick={handleApprove}
            disabled={submitting || !video?.videoId}
            className={`inline-flex items-center gap-2 rounded-md px-6 py-3 text-sm font-medium text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60 ${
              video?.videoId ? "bg-green-600 hover:bg-green-700" : "bg-muted text-muted-foreground"
            }`}
          >
            <CheckCircle2 className="h-5 w-5" />
            确认成片
          </button>
          <button
            onClick={() => setShowFeedback((value) => !value)}
            disabled={!video?.videoId}
            className="inline-flex items-center gap-2 rounded-md border border-amber-500/50 bg-amber-50 px-6 py-3 text-sm font-medium text-amber-700 hover:bg-amber-100 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <Wrench className="h-5 w-5" />
            请求修复
          </button>
          {!video?.videoId && (
            <p className="text-sm text-muted-foreground">等待成片记录写入后才能确认或提交修复。</p>
          )}
        </section>
      )}

      {showFeedback && (
        <section className="space-y-4 rounded-lg border bg-card p-6">
          <h2 className="font-semibold">反馈修复请求</h2>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label className="text-sm font-medium">问题类型</label>
              <select
                value={feedbackCategory}
                onChange={(e) => setFeedbackCategory(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm"
              >
                {FEEDBACK_CATEGORIES.map((category) => (
                  <option key={category.value} value={category.value}>
                    {category.label}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-sm font-medium">修复目标</label>
              <select
                value={feedbackTarget}
                onChange={(e) => setFeedbackTarget(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm"
              >
                {Object.entries(TARGET_TYPE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="text-sm font-medium">详细描述</label>
            <textarea
              value={feedbackText}
              onChange={(e) => setFeedbackText(e.target.value)}
              rows={4}
              placeholder="请描述你发现的问题，例如：第 2 个镜头没有展示背面印花、整体光线太暗、商品颜色偏差太大。"
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm"
            />
          </div>
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowFeedback(false)} className="rounded-md border px-4 py-2 text-sm hover:bg-accent">
              取消
            </button>
            <button
              onClick={handleSubmitFeedback}
              disabled={submitting || !feedbackText.trim()}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {submitting ? "提交中..." : "提交反馈"}
            </button>
          </div>
        </section>
      )}

      {isCompleted && (
        <section className="flex flex-col items-center gap-4 rounded-lg border border-green-500/50 bg-green-50 p-12 text-center">
          <CheckCircle2 className="h-12 w-12 text-green-500" />
          <p className="text-xl font-semibold text-green-700">审核通过</p>
          <p className="text-sm text-green-600">视频已完成，可以进入视频库下载或导出。</p>
        </section>
      )}

      {repairEvents.length > 0 && (
        <section className="space-y-4">
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <Clock className="h-5 w-5" />
            修复历史
          </h2>
          {repairEvents.map((event) => (
            <div key={event.repairEventId} className="rounded-lg border bg-card p-4">
              <div className="mb-2 flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                    {TARGET_TYPE_LABELS[event.targetType] || event.targetType}
                  </span>
                  {event.issueType && <span className="text-xs text-muted-foreground">{event.issueType}</span>}
                </div>
                <span
                  className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                    event.status === "completed"
                      ? "bg-green-100 text-green-700"
                      : event.status === "failed"
                        ? "bg-red-100 text-red-700"
                        : event.status === "in_progress"
                          ? "bg-blue-100 text-blue-700"
                          : "bg-muted text-muted-foreground"
                  }`}
                >
                  {eventStatusLabel(event.status)}
                </span>
              </div>
              <p className="text-sm">{event.userFeedback}</p>
              <p className="mt-1 text-xs text-muted-foreground">{new Date(event.createdAt).toLocaleString()}</p>
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
