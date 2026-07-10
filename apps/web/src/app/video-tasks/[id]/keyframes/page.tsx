"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";
import { ArrowLeft, Upload, Wand2, CheckCircle2, XCircle, Image as ImageIcon, Loader2, AlertCircle } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

interface KeyframeItem {
  keyframeId: string;
  taskId: string;
  shotId?: string | null;
  shotNo: number;
  source: string;
  assetId?: string | null;
  imagePurpose: string;
  url?: string | null;
  prompt?: string | null;
  userInstruction?: string | null;
  provider?: string | null;
  modelName?: string | null;
  status: string;
  version: number;
  errorMessage?: string | null;
  createdAt: string;
}

interface StoryboardShot {
  shotNo: number;
  duration: number;
  scene: string;
  action?: string;
  subtitle: string;
  materialType: string;
  prompt?: string;
  editInstruction?: string;
}

interface StoryboardData {
  storyboardId: string;
  shots?: StoryboardShot[];
}

const PURPOSE_LABELS: Record<string, string> = {
  first_frame: "首帧", last_frame: "末帧", reference: "参考", product_detail: "商品细节",
};

export default function KeyframesPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [keyframes, setKeyframes] = useState<KeyframeItem[]>([]);
  const [shots, setShots] = useState<StoryboardShot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Upload modal per shot
  const [activeShot, setActiveShot] = useState<number | null>(null);
  const [uploadMode, setUploadMode] = useState<"upload" | "generate" | null>(null);
  const [uploadUrl, setUploadUrl] = useState("");
  const [uploadPurpose, setUploadPurpose] = useState("first_frame");
  const [genPrompt, setGenPrompt] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [batchGenerating, setBatchGenerating] = useState(false);

  const load = useCallback(async () => {
    try {
      const [taskRes, kfRes, sbRes] = await Promise.all([
        apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
        apiRequest<{ code: number; message: string; data: { taskId: string; keyframes: KeyframeItem[] } }>(
          `/api/video-tasks/${id}/keyframes`
        ),
        apiRequest<{ code: number; message: string; data: StoryboardData }>(
          `/api/video-tasks/${id}/storyboard`
        ),
      ]);
      if (taskRes.code === 0 && taskRes.data) setTask(taskRes.data);
      if (kfRes.code === 0 && kfRes.data) setKeyframes(kfRes.data.keyframes || []);
      if (sbRes.code === 0 && sbRes.data) setShots(sbRes.data.shots || []);
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

  function getKeyframeForShot(shotNo: number): KeyframeItem | undefined {
    return keyframes.find((k) => k.shotNo === shotNo);
  }

  async function handleUpload(shotNo: number) {
    if (!uploadUrl.trim()) return;
    setSubmitting(true);
    setError("");
    try {
      await apiRequest(`/api/video-tasks/${id}/keyframes`, {
        method: "POST",
        body: { shotNo, source: "user_upload", imagePurpose: uploadPurpose, url: uploadUrl.trim() },
      });
      setActiveShot(null);
      setUploadMode(null);
      setUploadUrl("");
      load();
    } catch (e: any) {
      setError(e.message || "上传失败");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRequestAI(shotNo: number) {
    if (!genPrompt.trim()) return;
    setSubmitting(true);
    setError("");
    try {
      await apiRequest(`/api/video-tasks/${id}/keyframes`, {
        method: "POST",
        body: { shotNo, source: "ai_generated", imagePurpose: uploadPurpose, prompt: genPrompt.trim() },
      });
      setActiveShot(null);
      setUploadMode(null);
      setGenPrompt("");
      load();
    } catch (e: any) {
      setError(e.message || "请求失败");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleConfirm(keyframeId: string) {
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; status: string } }>(
        `/api/video-tasks/${id}/keyframes/${keyframeId}/confirm`,
        { method: "POST", body: { confirmed: true } }
      );
      if (res.code === 0 && res.data) {
        load();
        if (res.data.status === "video_clip_generating") {
          router.push(`/video-tasks/${id}/clips`);
        }
      }
    } catch (e: any) {
      setError(e.message || "确认失败");
    }
  }

  async function handleReject(keyframeId: string) {
    try {
      await apiRequest(`/api/video-tasks/${id}/keyframes/${keyframeId}/reject`, {
        method: "POST", body: { confirmed: false, feedback: "" },
      });
      load();
    } catch (e: any) {
      setError(e.message || "驳回失败");
    }
  }

  async function handleGenerateAll() {
    setBatchGenerating(true);
    setError("");
    try {
      await apiRequest(`/api/video-tasks/${id}/keyframes/generate`, { method: "POST" });
      load();
    } catch (e: any) {
      setError(e.message || "批量生成失败");
    } finally {
      setBatchGenerating(false);
    }
  }

  async function handleRegenerate(keyframeId: string) {
    setSubmitting(true);
    setError("");
    try {
      await apiRequest(`/api/video-tasks/${id}/keyframes/${keyframeId}/regenerate`, { method: "POST" });
      load();
    } catch (e: any) {
      setError(e.message || "重新生成失败");
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

  const canConfigure = task && ["keyframe_configuring", "waiting_image_confirmation"].includes(task.status);
  const isGenerating = task?.status === "image_generating";
  const allConfirmed = shots.length > 0 && shots.every((s) => {
    const kf = getKeyframeForShot(s.shotNo ?? 0);
    return kf?.status === "confirmed";
  });
  const unconfiguredShots = shots.filter((s) => {
    const kf = getKeyframeForShot(s.shotNo ?? 0);
    return !kf || kf.status === "draft" || kf.status === "rejected" || kf.status === "failed";
  });

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <div className="flex items-center justify-between">
        <div>
          <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="mr-1 inline h-3 w-3" />返回工作台
          </Link>
          <h1 className="mt-2 text-2xl font-bold">关键帧配置</h1>
          <p className="text-sm text-muted-foreground">
            {isGenerating ? "AI 正在生成关键帧…" : "为每个分镜镜头配置关键帧图片"}
          </p>
        </div>
        <CancelTaskButton taskId={id} />
        {allConfirmed && canConfigure && (
          <Link href={`/video-tasks/${id}/clips`}
            className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700">
            <CheckCircle2 className="h-4 w-4" />全部已确认，进入片段 →
          </Link>
        )}
      </div>

      {/* Batch generate banner */}
      {canConfigure && unconfiguredShots.length > 0 && (
        <div className="rounded-lg border-2 border-dashed border-primary/50 bg-primary/5 p-6">
          <div className="flex items-center justify-between">
            <div>
              <p className="font-semibold text-lg">{unconfiguredShots.length} 个镜头等待生成关键帧</p>
              <p className="text-sm text-muted-foreground mt-1">一键为所有未配置的镜头触发 AI 关键帧生成</p>
            </div>
            <button onClick={handleGenerateAll} disabled={batchGenerating}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-6 py-3 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50 shadow-sm">
              <Wand2 className="h-4 w-4" />
              {batchGenerating ? "触发中..." : "一键全部 AI 生成"}
            </button>
          </div>
        </div>
      )}

      {error && <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}

      {isGenerating && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
          <p className="text-lg font-medium">AI 正在生成关键帧图片</p>
          <p className="text-sm text-muted-foreground">根据分镜脚本和素材风格生成每个镜头的关键帧…</p>
        </div>
      )}

      {shots.length === 0 ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <ImageIcon className="mx-auto h-10 w-10 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">暂无分镜数据</p>
          <p className="mt-1 text-sm text-muted-foreground">请先确认分镜后再配置关键帧</p>
        </div>
      ) : (
        <div className="space-y-4">
          {shots.map((shot) => {
            const shotNo = shot.shotNo ?? 0;
            const kf = getKeyframeForShot(shotNo);
            const isActive = activeShot === shotNo;

            return (
              <div key={shotNo} className={`rounded-lg border bg-card p-4 ${kf?.status === "confirmed" ? "border-green-500/50" : kf?.status === "rejected" ? "border-destructive/30" : ""}`}>
                <div className="flex items-start justify-between mb-3">
                  <div>
                    <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
                      镜头 {shotNo} · {shot.duration}s
                    </span>
                    <p className="mt-1 text-sm font-medium">{shot.scene}</p>
                    <p className="text-xs text-muted-foreground">{shot.subtitle}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {kf?.status === "confirmed" ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700">
                        <CheckCircle2 className="h-3 w-3" />已确认
                      </span>
                    ) : kf?.status === "rejected" ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700">
                        <XCircle className="h-3 w-3" />已驳回
                      </span>
                    ) : kf ? (
                      <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
                        <AlertCircle className="h-3 w-3" />待确认
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                        未配置
                      </span>
                    )}
                  </div>
                </div>

                {/* Keyframe preview */}
                {kf?.url && (
                  <img src={kf.url} alt={`Shot ${shotNo}`}
                    className="w-full h-40 object-cover rounded-md mb-3 bg-muted" />
                )}

                {/* Generating state */}
                {kf?.status === "generating" && (
                  <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
                    <Loader2 className="h-4 w-4 animate-spin" />AI 生成中…
                  </div>
                )}

                {/* Actions */}
                {canConfigure && (
                  <div className="flex items-center gap-2">
                    {(!kf || kf.status === "rejected" || kf.status === "draft" || kf.status === "failed") ? (
                      <>
                        <button onClick={() => { setActiveShot(shotNo); setUploadMode("upload"); setUploadPurpose("first_frame"); }}
                          className="inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-xs font-medium hover:bg-accent">
                          <Upload className="h-3.5 w-3.5" />上传图片
                        </button>
                        <button onClick={() => {
                          setActiveShot(shotNo); setUploadMode("generate");
                          setUploadPurpose("first_frame");
                          setGenPrompt(shot.prompt || `Fashion keyframe: ${shot.scene}, ${shot.subtitle}, 9:16 vertical, TikTok style`);
                        }}
                          className="inline-flex items-center gap-1.5 rounded-md border px-3 py-1.5 text-xs font-medium hover:bg-accent">
                          <Wand2 className="h-3.5 w-3.5" />AI 生成
                        </button>
                        {(kf?.status === "rejected" || kf?.status === "failed") && (
                          <button onClick={() => handleRegenerate(kf.keyframeId)}
                            className="inline-flex items-center gap-1.5 rounded-md border border-amber-500/50 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-700 hover:bg-amber-100">
                            <Wand2 className="h-3.5 w-3.5" />重新生成
                          </button>
                        )}
                      </>
                    ) : kf.status === "uploaded" || kf.status === "generated" ? (
                      <>
                        <button onClick={() => handleConfirm(kf.keyframeId)}
                          className="inline-flex items-center gap-1.5 rounded-md bg-green-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-green-700">
                          <CheckCircle2 className="h-3.5 w-3.5" />确认
                        </button>
                        <button onClick={() => handleReject(kf.keyframeId)}
                          className="inline-flex items-center gap-1.5 rounded-md border border-destructive/50 px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/5">
                          <XCircle className="h-3.5 w-3.5" />驳回
                        </button>
                      </>
                    ) : null}
                  </div>
                )}

                {/* Upload / Generate panel for this shot */}
                {isActive && canConfigure && (
                  <div className="mt-4 rounded-md border bg-muted/50 p-4 space-y-3">
                    <div className="flex items-center gap-4">
                      <button onClick={() => setUploadMode("upload")}
                        className={`text-sm font-medium ${uploadMode === "upload" ? "text-primary" : "text-muted-foreground"}`}>
                        上传图片
                      </button>
                      <button onClick={() => setUploadMode("generate")}
                        className={`text-sm font-medium ${uploadMode === "generate" ? "text-primary" : "text-muted-foreground"}`}>
                        AI 生成
                      </button>
                      <div className="flex-1" />
                      <select value={uploadPurpose} onChange={(e) => setUploadPurpose(e.target.value)}
                        className="rounded border px-2 py-1 text-xs">
                        {Object.entries(PURPOSE_LABELS).map(([k, v]) => (
                          <option key={k} value={k}>{v}</option>
                        ))}
                      </select>
                    </div>

                    {uploadMode === "upload" ? (
                      <>
                        <input type="text" value={uploadUrl} onChange={(e) => setUploadUrl(e.target.value)}
                          placeholder="https://cos.example.com/keyframe.jpg"
                          className="w-full rounded-md border px-3 py-2 text-sm" />
                        <div className="flex justify-end gap-2">
                          <button onClick={() => { setActiveShot(null); setUploadMode(null); }}
                            className="rounded-md border px-3 py-1.5 text-xs hover:bg-accent">取消</button>
                          <button onClick={() => handleUpload(shotNo)} disabled={submitting || !uploadUrl.trim()}
                            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50">
                            {submitting ? "上传中..." : "确认上传"}
                          </button>
                        </div>
                      </>
                    ) : (
                      <>
                        <textarea value={genPrompt} onChange={(e) => setGenPrompt(e.target.value)}
                          rows={3} placeholder="描述你想要的关键帧画面…"
                          className="w-full rounded-md border px-3 py-2 text-sm" />
                        <div className="flex justify-end gap-2">
                          <button onClick={() => { setActiveShot(null); setUploadMode(null); }}
                            className="rounded-md border px-3 py-1.5 text-xs hover:bg-accent">取消</button>
                          <button onClick={() => handleRequestAI(shotNo)} disabled={submitting || !genPrompt.trim()}
                            className="rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50">
                            <Wand2 className="mr-1 inline h-3.5 w-3.5" />
                            {submitting ? "请求中..." : "请求 AI 生成"}
                          </button>
                        </div>
                      </>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
