"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";
import { ArrowLeft, CheckCircle2, XCircle, Play, Loader2, AlertCircle, Film } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

interface ClipItem {
  clipId: string;
  taskId: string;
  shotId?: string | null;
  keyframeId?: string | null;
  shotNo: number;
  source: string;
  url?: string | null;
  prompt?: string | null;
  provider?: string | null;
  modelName?: string | null;
  status: string;
  duration: number;
  version: number;
  errorMessage?: string | null;
  createdAt: string;
}

export default function ClipsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [clips, setClips] = useState<ClipItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [rendering, setRendering] = useState(false);

  const load = useCallback(async () => {
    try {
      const [taskRes, clipRes] = await Promise.all([
        apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
        apiRequest<{ code: number; message: string; data: { taskId: string; clips: ClipItem[] } }>(
          `/api/video-tasks/${id}/video-clips`
        ),
      ]);
      if (taskRes.code === 0 && taskRes.data) setTask(taskRes.data);
      if (clipRes.code === 0 && clipRes.data) setClips(clipRes.data.clips || []);
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

  async function handleConfirm(clipId: string) {
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; status: string } }>(
        `/api/video-tasks/${id}/video-clips/${clipId}/confirm`,
        { method: "POST", body: { confirmed: true } }
      );
      if (res.code === 0) {
        if (res.data?.status === "rendering") {
          router.push(`/video-tasks/${id}/review`);
        } else {
          load();
        }
      }
    } catch (e: any) {
      setError(e.message || "确认失败");
    }
  }

  async function handleReject(clipId: string) {
    try {
      await apiRequest(`/api/video-tasks/${id}/video-clips/${clipId}/reject`, {
        method: "POST", body: { confirmed: false, feedback: "" },
      });
      load();
    } catch (e: any) {
      setError(e.message || "驳回失败");
    }
  }

  async function handleRender() {
    setRendering(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; status: string } }>(
        `/api/video-tasks/${id}/render`,
        { method: "POST" }
      );
      if (res.code === 0) {
        router.push(`/video-tasks/${id}/review`);
      } else {
        setError(res.message || "渲染请求失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setRendering(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  const isGenerating = task?.status === "video_clip_generating";
  const canManage = task && ["waiting_video_clip_confirmation", "video_clip_generating"].includes(task.status);
  const allConfirmed = clips.length > 0 && clips.every((c) => c.status === "confirmed");

  const STATUS_ICON: Record<string, React.ReactNode> = {
    confirmed: <CheckCircle2 className="h-4 w-4 text-green-500" />,
    uploaded: <AlertCircle className="h-4 w-4 text-amber-500" />,
    generated: <AlertCircle className="h-4 w-4 text-amber-500" />,
    rejected: <XCircle className="h-4 w-4 text-red-500" />,
    generating: <Loader2 className="h-4 w-4 animate-spin text-blue-500" />,
    draft: <Loader2 className="h-4 w-4 text-muted-foreground" />,
  };

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <div className="flex items-center justify-between">
        <div>
          <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="mr-1 inline h-3 w-3" />返回工作台
          </Link>
          <h1 className="mt-2 text-2xl font-bold">视频片段</h1>
          <p className="text-sm text-muted-foreground">
            {isGenerating ? "AI 正在生成视频片段…" : "预览并确认每个镜头的视频片段"}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <CancelTaskButton taskId={id} />
          {allConfirmed && canManage && (
            <button onClick={handleRender} disabled={rendering}
              className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50">
              <Film className="h-4 w-4" />
              {rendering ? "请求中..." : "开始渲染成片"}
            </button>
          )}
        </div>
      </div>

      {error && <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}

      {isGenerating && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
          <p className="text-lg font-medium">AI 正在生成视频片段</p>
          <p className="text-sm text-muted-foreground">基于已确认的关键帧和分镜脚本生成每个镜头的短视频…</p>
        </div>
      )}

      {clips.length === 0 && !isGenerating ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <Play className="mx-auto h-10 w-10 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">暂无视频片段</p>
          <p className="mt-1 text-sm text-muted-foreground">请先在关键帧页面确认所有关键帧</p>
          <Link href={`/video-tasks/${id}/keyframes`}
            className="mt-4 inline-block text-sm font-medium text-primary hover:underline">
            ← 返回关键帧
          </Link>
        </div>
      ) : (
        <div className="space-y-4">
          {clips.map((clip) => (
            <div key={clip.clipId} className={`rounded-lg border bg-card p-4 ${clip.status === "confirmed" ? "border-green-500/50" : clip.status === "rejected" ? "border-destructive/30" : ""}`}>
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-3">
                  <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
                    镜头 {clip.shotNo}
                  </span>
                  <span className="text-sm text-muted-foreground">{clip.duration}s</span>
                  <span className="text-xs text-muted-foreground">{clip.source === "ai_generated" ? "AI 生成" : "用户上传"}</span>
                </div>
                <div className="flex items-center gap-2">
                  {STATUS_ICON[clip.status] || null}
                  <span className="text-xs font-medium">
                    {clip.status === "confirmed" ? "已确认" : clip.status === "rejected" ? "已驳回" :
                     clip.status === "generating" ? "生成中" : clip.status === "generated" ? "待确认" :
                     clip.status === "uploaded" ? "待确认" : clip.status}
                  </span>
                </div>
              </div>

              {/* Video preview */}
              {clip.url && (
                <video src={clip.url} controls preload="metadata"
                  className="w-full rounded-md bg-black mb-3" style={{ maxHeight: 320 }} />
              )}

              {clip.prompt && (
                <p className="text-xs text-muted-foreground mb-3 line-clamp-2">Prompt: {clip.prompt}</p>
              )}

              {/* Actions */}
              {canManage && (clip.status === "uploaded" || clip.status === "generated") && (
                <div className="flex items-center gap-2">
                  <button onClick={() => handleConfirm(clip.clipId)}
                    className="inline-flex items-center gap-1.5 rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700">
                    <CheckCircle2 className="h-3.5 w-3.5" />确认
                  </button>
                  <button onClick={() => handleReject(clip.clipId)}
                    className="inline-flex items-center gap-1.5 rounded-md border border-destructive/50 px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/5">
                    <XCircle className="h-3.5 w-3.5" />驳回
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
