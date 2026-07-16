"use client";

import { CancelTaskButton } from "@/components/cancel-task-button";
import { apiRequest } from "@/lib/api-client";
import type { VideoTask } from "@/types/api";
import { AlertCircle, ArrowLeft, CheckCircle2, Film, Loader2, Play, RefreshCw, XCircle } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

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
  const [busyAction, setBusyAction] = useState<string | null>(null);

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

  const isGenerating = task?.status === "video_clip_generating";
  const canGenerate = task && ["waiting_image_confirmation", "waiting_video_clip_confirmation"].includes(task.status);
  const canManage = task?.status === "waiting_video_clip_confirmation";
  const allConfirmed = clips.length > 0 && clips.every((c) => c.status === "confirmed");
  const hasRetryableClip = useMemo(
    () => clips.some((clip) => clip.status === "rejected" || clip.status === "failed"),
    [clips]
  );

  async function runAction(actionKey: string, fn: () => Promise<void>) {
    setBusyAction(actionKey);
    setError("");
    try {
      await fn();
      await load();
    } catch (e: any) {
      setError(e.message || "操作失败");
    } finally {
      setBusyAction(null);
    }
  }

  async function handleGenerateAll() {
    await runAction("generate-all", async () => {
      await apiRequest(`/api/video-tasks/${id}/video-clips/generate`, { method: "POST" });
    });
  }

  async function handleRegenerate(clipId: string) {
    await runAction(`regenerate-${clipId}`, async () => {
      await apiRequest(`/api/video-tasks/${id}/video-clips/${clipId}/regenerate`, { method: "POST" });
    });
  }

  async function handleConfirm(clipId: string) {
    await runAction(`confirm-${clipId}`, async () => {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; status: string } }>(
        `/api/video-tasks/${id}/video-clips/${clipId}/confirm`,
        { method: "POST", body: { confirmed: true } }
      );
      if (res.code === 0 && res.data?.status === "rendering") {
        router.push(`/video-tasks/${id}/review`);
      }
    });
  }

  async function handleReject(clipId: string) {
    await runAction(`reject-${clipId}`, async () => {
      await apiRequest(`/api/video-tasks/${id}/video-clips/${clipId}/reject`, {
        method: "POST",
        body: { confirmed: false, feedback: "" },
      });
    });
  }

  async function handleRender() {
    await runAction("render", async () => {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; status: string } }>(
        `/api/video-tasks/${id}/render`,
        { method: "POST" }
      );
      if (res.code === 0) {
        router.push(`/video-tasks/${id}/review`);
      } else {
        throw new Error(res.message || "渲染请求失败");
      }
    });
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  const statusIcon: Record<string, React.ReactNode> = {
    confirmed: <CheckCircle2 className="h-4 w-4 text-green-500" />,
    uploaded: <AlertCircle className="h-4 w-4 text-amber-500" />,
    generated: <AlertCircle className="h-4 w-4 text-amber-500" />,
    rejected: <XCircle className="h-4 w-4 text-red-500" />,
    failed: <XCircle className="h-4 w-4 text-red-500" />,
    generating: <Loader2 className="h-4 w-4 animate-spin text-blue-500" />,
    draft: <Loader2 className="h-4 w-4 text-muted-foreground" />,
  };

  const statusLabel: Record<string, string> = {
    confirmed: "已确认",
    rejected: "已驳回",
    failed: "生成失败",
    generating: "生成中",
    generated: "待确认",
    uploaded: "待确认",
    draft: "待生成",
  };

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <div className="flex items-center justify-between">
        <div>
          <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="mr-1 inline h-3 w-3" />
            返回工作台
          </Link>
          <h1 className="mt-2 text-2xl font-bold">视频片段</h1>
          <p className="text-sm text-muted-foreground">
            {isGenerating ? "AI 正在生成视频片段…" : "预览并确认每个镜头的视频片段"}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <CancelTaskButton taskId={id} />
          {canGenerate && (
            <button
              onClick={handleGenerateAll}
              disabled={Boolean(busyAction) || isGenerating}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {busyAction === "generate-all" || isGenerating ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Play className="h-4 w-4" />
              )}
              {hasRetryableClip ? "生成缺失/失败片段" : "生成视频片段"}
            </button>
          )}
          {allConfirmed && canManage && (
            <button
              onClick={handleRender}
              disabled={Boolean(busyAction)}
              className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
            >
              {busyAction === "render" ? <Loader2 className="h-4 w-4 animate-spin" /> : <Film className="h-4 w-4" />}
              开始渲染成片
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
          <p className="mt-1 text-sm text-muted-foreground">请先确认全部关键帧，或点击生成视频片段。</p>
          <div className="mt-4 flex justify-center gap-4">
            <Link href={`/video-tasks/${id}/keyframes`} className="text-sm font-medium text-primary hover:underline">
              返回关键帧
            </Link>
            {canGenerate && (
              <button onClick={handleGenerateAll} className="text-sm font-medium text-primary hover:underline">
                生成视频片段
              </button>
            )}
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          {clips.map((clip) => (
            <div
              key={clip.clipId}
              className={`rounded-lg border bg-card p-4 ${
                clip.status === "confirmed"
                  ? "border-green-500/50"
                  : clip.status === "rejected" || clip.status === "failed"
                    ? "border-destructive/30"
                    : ""
              }`}
            >
              <div className="mb-3 flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
                    镜头 {clip.shotNo}
                  </span>
                  <span className="text-sm text-muted-foreground">{clip.duration || "-"}s</span>
                  <span className="text-xs text-muted-foreground">
                    {clip.source === "ai_generated" ? "AI 生成" : "用户上传"}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  {statusIcon[clip.status] || null}
                  <span className="text-xs font-medium">{statusLabel[clip.status] || clip.status}</span>
                </div>
              </div>

              {clip.url ? (
                <video src={clip.url} controls preload="metadata" className="mb-3 w-full rounded-md bg-black" style={{ maxHeight: 320 }} />
              ) : (
                <div className="mb-3 flex h-40 items-center justify-center rounded-md bg-muted text-sm text-muted-foreground">
                  {clip.status === "generating" ? "正在生成预览…" : "暂无视频预览"}
                </div>
              )}

              {clip.errorMessage && (
                <p className="mb-3 rounded-md bg-destructive/10 p-2 text-xs text-destructive">{clip.errorMessage}</p>
              )}

              {clip.prompt && <p className="mb-3 line-clamp-2 text-xs text-muted-foreground">Prompt: {clip.prompt}</p>}

              {canManage && (
                <div className="flex items-center gap-2">
                  {(clip.status === "uploaded" || clip.status === "generated") && (
                    <>
                      <button
                        onClick={() => handleConfirm(clip.clipId)}
                        disabled={Boolean(busyAction)}
                        className="inline-flex items-center gap-1.5 rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50"
                      >
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        确认
                      </button>
                      <button
                        onClick={() => handleReject(clip.clipId)}
                        disabled={Boolean(busyAction)}
                        className="inline-flex items-center gap-1.5 rounded-md border border-destructive/50 px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/5 disabled:opacity-50"
                      >
                        <XCircle className="h-3.5 w-3.5" />
                        驳回
                      </button>
                    </>
                  )}
                  {(clip.status === "rejected" || clip.status === "failed") && (
                    <button
                      onClick={() => handleRegenerate(clip.clipId)}
                      disabled={Boolean(busyAction)}
                      className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                    >
                      {busyAction === `regenerate-${clip.clipId}` ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <RefreshCw className="h-3.5 w-3.5" />
                      )}
                      重新生成
                    </button>
                  )}
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
