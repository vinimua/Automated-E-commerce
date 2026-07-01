"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask, Video } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";
import { ArrowLeft, CheckCircle2, Wrench, Loader2, Clock, AlertCircle } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

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
  storyboard: "分镜", keyframe: "关键帧", video_clip: "视频片段",
  render_manifest: "渲染清单", final_video: "成片", plan: "方案",
};

const FEEDBACK_CATEGORIES = [
  { value: "visual_quality", label: "画质问题" },
  { value: "product_accuracy", label: "商品不准确" },
  { value: "lighting_issue", label: "光线问题" },
  { value: "action_stiffness", label: "动作僵硬" },
  { value: "missing_detail", label: "细节缺失" },
  { value: "layout_composition", label: "构图问题" },
  { value: "style_mismatch", label: "风格不匹配" },
  { value: "other", label: "其他" },
];

export default function ReviewPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [video, setVideo] = useState<Video | null>(null);
  const [repairEvents, setRepairEvents] = useState<RepairEventItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Feedback form
  const [showFeedback, setShowFeedback] = useState(false);
  const [feedbackCategory, setFeedbackCategory] = useState("visual_quality");
  const [feedbackTarget, setFeedbackTarget] = useState("video_clip");
  const [feedbackText, setFeedbackText] = useState("");
  const [submitting, setSubmitting] = useState(false);

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
        // Find matching video
        if (["completed", "exported", "waiting_final_review", "rendering", "repairing"].includes(taskRes.data.status)) {
          try {
            const vRes = await apiRequest<{ code: number; message: string; data: { items: Video[] } }>(
              `/api/videos?productId=${taskRes.data.productId}`
            );
            const matched = vRes.data?.items?.find((v) => v.taskId === id);
            if (matched) setVideo(matched);
          } catch { /* video may not exist yet */ }
        }
      }
      if (repairRes.code === 0 && repairRes.data) setRepairEvents(repairRes.data.events || []);
    } catch (e: any) {
      setError(e.message || "加载失败");
    } finally {
      setLoading(false);
    }
  }, [id]);

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
        body: { feedbackText: feedbackText.trim(), category: feedbackCategory, targetType: feedbackTarget },
      });
      setShowFeedback(false);
      setFeedbackText("");
      load();
    } catch (e: any) {
      setError(e.message || "提交失败");
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
      if (res.code === 0) {
        if (video?.videoId) {
          router.push(`/videos/${video.videoId}`);
        } else {
          load();
        }
      } else {
        setError(res.message || "批准失败");
      }
    } catch (e: any) {
      setError(e.message || "批准失败");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  const isRendering = task?.status === "rendering";
  const isReviewing = task?.status === "waiting_final_review";
  const isRepairing = task?.status === "repairing";
  const isCompleted = task?.status === "completed" || task?.status === "exported";

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <div>
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="mr-1 inline h-3 w-3" />返回工作台
        </Link>
        <h1 className="mt-2 text-2xl font-bold">成片审核</h1>
        <p className="text-sm text-muted-foreground">
          {isRendering ? "正在渲染视频…" : isReviewing ? "请审核成片" : isRepairing ? "修复中…" : isCompleted ? "审核完成" : ""}
        </p>
        {!isCompleted && <div className="mt-3"><CancelTaskButton taskId={id} /></div>}
      </div>

      {error && <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}

      {/* Rendering spinner */}
      {isRendering && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
          <p className="text-lg font-medium">正在渲染成片</p>
          <p className="text-sm text-muted-foreground">正在将所有已确认片段合成为最终视频…</p>
        </div>
      )}

      {/* Repairing */}
      {isRepairing && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12">
          <Wrench className="h-10 w-10 animate-pulse text-amber-500" />
          <p className="text-lg font-medium">修复进行中</p>
          <p className="text-sm text-muted-foreground">AI 正在根据你的反馈修复视频…</p>
        </div>
      )}

      {/* Video player */}
      {video?.videoUrl && (isReviewing || isCompleted) && (
        <div className="rounded-lg border bg-card overflow-hidden">
          <video src={video.videoUrl} controls className="w-full bg-black" style={{ maxHeight: 480 }} />
          <div className="p-4 flex items-center justify-between">
            <div>
              <p className="font-medium">{video.title || "生成视频"}</p>
              <p className="text-sm text-muted-foreground">
                {video.duration}s · {video.resolution || "1080x1920"} · {video.qualityScore != null ? `质量分: ${video.qualityScore}` : ""}
              </p>
            </div>
            {video.videoUrl && (
              <a href={video.videoUrl} target="_blank" rel="noopener noreferrer"
                className="text-sm font-medium text-primary hover:underline">
                下载视频
              </a>
            )}
          </div>
        </div>
      )}

      {/* Review actions */}
      {isReviewing && (
        <div className="flex items-center gap-4">
          <button onClick={handleApprove} disabled={submitting || !video?.videoId}
            className={`inline-flex items-center gap-2 rounded-md px-6 py-3 text-sm font-medium text-white hover:opacity-90 ${video?.videoId ? "bg-green-600 hover:bg-green-700" : "bg-muted text-muted-foreground cursor-not-allowed"}`}>
            <CheckCircle2 className="h-5 w-5" />批准成片
          </button>
          <button onClick={() => setShowFeedback(!showFeedback)}
            className="inline-flex items-center gap-2 rounded-md border border-amber-500/50 bg-amber-50 px-6 py-3 text-sm font-medium text-amber-700 hover:bg-amber-100">
            <Wrench className="h-5 w-5" />请求修复
          </button>
        </div>
      )}

      {/* Feedback form */}
      {showFeedback && (
        <div className="rounded-lg border bg-card p-6 space-y-4">
          <h3 className="font-semibold">反馈修复请求</h3>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium">问题类型</label>
              <select value={feedbackCategory} onChange={(e) => setFeedbackCategory(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm">
                {FEEDBACK_CATEGORIES.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-sm font-medium">修复目标</label>
              <select value={feedbackTarget} onChange={(e) => setFeedbackTarget(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm">
                {Object.entries(TARGET_TYPE_LABELS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
          </div>
          <div>
            <label className="text-sm font-medium">详细描述</label>
            <textarea value={feedbackText} onChange={(e) => setFeedbackText(e.target.value)}
              rows={4} placeholder="请描述你发现的问题，例如：背面印花没有展示、光线太暗…"
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm" />
          </div>
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowFeedback(false)}
              className="rounded-md border px-4 py-2 text-sm hover:bg-accent">取消</button>
            <button onClick={handleSubmitFeedback} disabled={submitting || !feedbackText.trim()}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50">
              {submitting ? "提交中..." : "提交反馈"}
            </button>
          </div>
        </div>
      )}

      {/* Completed state */}
      {isCompleted && (
        <div className="flex flex-col items-center gap-4 rounded-lg border border-green-500/50 bg-green-50 p-12">
          <CheckCircle2 className="h-12 w-12 text-green-500" />
          <p className="text-xl font-semibold text-green-700">审核通过</p>
          <p className="text-sm text-green-600">视频已完成，可以下载或导出到 TikTok Shop</p>
        </div>
      )}

      {/* Repair events history */}
      {repairEvents.length > 0 && (
        <div className="space-y-4">
          <h2 className="text-lg font-semibold flex items-center gap-2">
            <Clock className="h-5 w-5" />修复历史
          </h2>
          {repairEvents.map((event) => (
            <div key={event.repairEventId} className="rounded-lg border bg-card p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-2">
                  <span className="inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                    {TARGET_TYPE_LABELS[event.targetType] || event.targetType}
                  </span>
                  {event.issueType && (
                    <span className="text-xs text-muted-foreground">{event.issueType}</span>
                  )}
                </div>
                <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                  event.status === "completed" ? "bg-green-100 text-green-700" :
                  event.status === "failed" ? "bg-red-100 text-red-700" :
                  event.status === "in_progress" ? "bg-blue-100 text-blue-700" :
                  "bg-muted text-muted-foreground"
                }`}>
                  {event.status === "completed" ? "已完成" : event.status === "in_progress" ? "进行中" :
                   event.status === "failed" ? "失败" : event.status}
                </span>
              </div>
              <p className="text-sm">{event.userFeedback}</p>
              <p className="mt-1 text-xs text-muted-foreground">
                {new Date(event.createdAt).toLocaleString()}
              </p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
