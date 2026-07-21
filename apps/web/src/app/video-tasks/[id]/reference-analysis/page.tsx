"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask } from "@/types/api";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ArrowLeft, Clock, Camera, Film } from "lucide-react";

interface CreativeStateData {
  creativeStateId?: string;
  taskId: string;
  product?: Record<string, unknown> | null;
  model?: Record<string, unknown> | null;
  scene?: Record<string, unknown> | null;
  outfit?: Record<string, unknown> | null;
  referenceVideo?: Record<string, unknown> | null;
  constraints?: Record<string, unknown> | null;
  userRequirements?: Record<string, unknown> | null;
  version: number;
}

interface ReferenceShot {
  shotNo: number;
  startTime?: number;
  endTime?: number;
  duration?: number;
  scene: string;
  action?: string;
  camera?: string;
  transition?: string;
  subtitle?: string;
  structureRole?: string;
}

export default function ReferenceAnalysisPage() {
  const { id } = useParams<{ id: string }>();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [creativeState, setCreativeState] = useState<CreativeStateData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const [taskRes, csRes] = await Promise.all([
          apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
          apiRequest<{ code: number; message: string; data: CreativeStateData }>(
            `/api/video-tasks/${id}/creative-state`
          ),
        ]);
        if (taskRes.code === 0 && taskRes.data) setTask(taskRes.data);
        if (csRes.code === 0 && csRes.data) setCreativeState(csRes.data);
      } catch (e: any) {
        setError(e.message || "加载失败");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [id]);

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  const refVideo = creativeState?.referenceVideo as Record<string, unknown> | null | undefined;
  const refShots = (refVideo?.shots as ReferenceShot[]) || [];
  const refUrl = refVideo?.url as string | undefined;
  const hasReference = Boolean(refUrl);
  const hasAnalysis = refShots.length > 0;
  const isAnalyzing = task?.status === "reference_analyzing";

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <div>
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="mr-1 inline h-3 w-3" />返回工作台
        </Link>
        <h1 className="mt-2 text-2xl font-bold">参考视频分析</h1>
        <p className="text-sm text-muted-foreground">
          {task?.status === "reference_analyzing" ? "AI 正在分析参考视频结构…" : "参考视频的镜头拆解和结构分析"}
        </p>
      </div>

      {error && <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}

      {/* Task-level error: reference analysis explicitly failed */}
      {task?.status === "failed" && task?.failedStage === "reference_analysis" && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-6">
          <div className="flex items-center gap-2 mb-2">
            <span className="inline-flex items-center rounded-full bg-destructive/20 px-2.5 py-0.5 text-xs font-medium text-destructive">
              分析失败
            </span>
          </div>
          <p className="text-sm text-destructive/80">
            {task?.errorMessage || "视频分析失败，请检查视频链接是否可访问后重试。"}
          </p>
          {task?.errorCode && (
            <p className="mt-1 text-xs text-muted-foreground">错误码: {task.errorCode}</p>
          )}
        </div>
      )}

      {!hasReference ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <Film className="mx-auto h-10 w-10 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">未上传参考视频</p>
          <p className="mt-1 text-sm text-muted-foreground">
            你可以在素材管理页面上传 <code className="rounded bg-muted px-1 text-xs">reference_video</code> 角色的视频。
          </p>
          <Link href={`/video-tasks/${id}/assets`}
            className="mt-4 inline-block text-sm font-medium text-primary hover:underline">
            ← 返回素材管理
          </Link>
        </div>
      ) : isAnalyzing ? (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12">
          <div className="h-10 w-10 animate-spin rounded-full border-4 border-primary border-t-transparent" />
          <p className="text-lg font-medium">AI 正在分析参考视频结构</p>
          <p className="text-sm text-muted-foreground">
            正在下载视频并逐帧拆解镜头的场景、运镜、转场和字幕…
          </p>
          {refUrl && <p className="text-xs text-muted-foreground truncate max-w-md">视频: {refUrl}</p>}
        </div>
      ) : !hasAnalysis ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <Film className="mx-auto h-10 w-10 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">参考视频分析尚未完成</p>
          <p className="mt-1 text-sm text-muted-foreground">
            AI 分析可能尚未开始或遇到错误，请稍后刷新页面。
          </p>
        </div>
      ) : (
        <>
          {/* Reference video overview */}
          <div className="grid grid-cols-3 gap-4">
            <div className="rounded-lg border bg-card p-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Clock className="h-4 w-4" />
                <span>总时长</span>
              </div>
              <p className="mt-1 text-2xl font-semibold">{refVideo?.duration != null ? `${refVideo.duration}s` : "—"}</p>
            </div>
            <div className="rounded-lg border bg-card p-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Camera className="h-4 w-4" />
                <span>镜头数</span>
              </div>
              <p className="mt-1 text-2xl font-semibold">{refShots.length}</p>
            </div>
            <div className="rounded-lg border bg-card p-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Film className="h-4 w-4" />
                <span>结构模式</span>
              </div>
              <p className="mt-1 text-sm font-medium">
                {(refVideo?.structure as string[])?.join(" → ") || "—"}
              </p>
            </div>
          </div>

          {/* Title & Hook */}
          <div className="rounded-lg border bg-card p-6 space-y-3">
            <div>
              <span className="text-xs text-muted-foreground">视频标题</span>
              <p className="text-lg font-semibold">{refVideo?.title as string || "—"}</p>
            </div>
            {refVideo?.hook != null && (
              <div>
                <span className="text-xs text-muted-foreground">开场钩子</span>
                <p className="text-sm">{String(refVideo.hook)}</p>
              </div>
            )}
          </div>

          {/* Reusable patterns & risk tips */}
          <div className="grid grid-cols-2 gap-4">
            {(() => {
              const patterns = refVideo?.reusablePatterns as string[] | undefined;
              return patterns && patterns.length > 0 && (
                <div className="rounded-lg border bg-card p-4">
                  <h3 className="text-sm font-semibold mb-2">可复用模式</h3>
                  <ul className="space-y-1">
                    {patterns.map((p, i) => (
                      <li key={i} className="text-sm text-muted-foreground">· {p}</li>
                    ))}
                  </ul>
                </div>
              );
            })()}
            {(() => {
              const tips = refVideo?.riskTips as string[] | undefined;
              return tips && tips.length > 0 && (
                <div className="rounded-lg border border-amber-500/30 bg-amber-50/50 p-4">
                  <h3 className="text-sm font-semibold text-amber-700 mb-2">风险提示</h3>
                  <ul className="space-y-1">
                    {tips.map((t, i) => (
                      <li key={i} className="text-sm text-amber-600">· {t}</li>
                    ))}
                  </ul>
                </div>
              );
            })()}
          </div>

          {/* Shot breakdown */}
          <div className="space-y-4">
            <h2 className="text-lg font-semibold">参考镜头拆解</h2>
            {refShots.map((shot, i) => (
              <div key={i} className="rounded-lg border bg-card p-4">
                <div className="flex items-center justify-between mb-3">
                  <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
                    镜头 {shot.shotNo}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {shot.duration != null ? `${shot.duration}s` : `${shot.startTime || 0}–${shot.endTime || 0}`}
                  </span>
                </div>
                <dl className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <dt className="text-xs text-muted-foreground">场景</dt>
                    <dd>{shot.scene || "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">动作</dt>
                    <dd>{shot.action || "—"}</dd>
                  </div>
                  {shot.camera && (
                    <div>
                      <dt className="text-xs text-muted-foreground">机位</dt>
                      <dd>{shot.camera}</dd>
                    </div>
                  )}
                  {shot.transition && (
                    <div>
                      <dt className="text-xs text-muted-foreground">转场</dt>
                      <dd>{shot.transition}</dd>
                    </div>
                  )}
                  {shot.subtitle && (
                    <div className="col-span-2">
                      <dt className="text-xs text-muted-foreground">字幕</dt>
                      <dd className="text-muted-foreground">{shot.subtitle}</dd>
                    </div>
                  )}
                  {shot.structureRole && (
                    <div className="col-span-2">
                      <dt className="text-xs text-muted-foreground">结构角色</dt>
                      <dd className="inline-flex items-center rounded bg-accent px-2 py-0.5 text-xs font-mono">{shot.structureRole}</dd>
                    </div>
                  )}
                </dl>
              </div>
            ))}
          </div>
        </>
      )}

      {/* Navigation */}
      {task && ["plan_generating", "waiting_plan_selection"].includes(task.status) && (
        <div className="flex items-center gap-2 rounded-md border border-blue-500/50 bg-blue-50 p-4 text-sm">
          <Link href={`/video-tasks/${id}/plans`} className="font-medium text-blue-700 hover:underline">
            进入方案选择 →
          </Link>
          <span className="text-blue-600">AI 已生成创意方案，请选择你喜欢的方案。</span>
        </div>
      )}
    </div>
  );
}
