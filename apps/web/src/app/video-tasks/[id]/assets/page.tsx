"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useCallback } from "react";
import { ArrowLeft, Upload, Image as ImageIcon, Video, Music, Pencil, CheckCircle2, Loader2 } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

interface AssetItem {
  assetId: string;
  taskId: string;
  assetKind: string;
  assetRole: string;
  source: string;
  url: string;
  fileName?: string | null;
  mimeType?: string | null;
  sizeBytes?: number | null;
  description?: string | null;
  confirmed: boolean;
  createdAt: string;
}

const ASSET_ROLE_LABELS: Record<string, string> = {
  product_front: "商品正面", product_back: "商品背面", product_detail: "商品细节",
  model_reference: "模特参考", scene_reference: "场景参考", outfit_reference: "穿搭参考",
  reference_video: "参考视频", user_keyframe: "用户关键帧", generated_result: "生成结果",
  ai_keyframe: "AI 关键帧", image_variant: "图片变体", video_clip: "视频片段",
  final_video: "最终成片", cover_image: "封面图",
};

const KIND_ICON: Record<string, React.ReactNode> = {
  image: <ImageIcon className="h-4 w-4" />,
  video: <Video className="h-4 w-4" />,
  audio: <Music className="h-4 w-4" />,
};

export default function AssetsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [assets, setAssets] = useState<AssetItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [confirming, setConfirming] = useState(false);

  // Upload form
  const [showUpload, setShowUpload] = useState(false);
  const [uploadKind, setUploadKind] = useState("image");
  const [uploadRole, setUploadRole] = useState("product_front");
  const [uploadUrl, setUploadUrl] = useState("");
  const [uploading, setUploading] = useState(false);

  // Edit role
  const [editingId, setEditingId] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [taskRes, assetRes] = await Promise.all([
        apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
        apiRequest<{ code: number; message: string; data: { taskId: string; assets: AssetItem[] } }>(
          `/api/video-tasks/${id}/assets`
        ),
      ]);
      if (taskRes.code === 0 && taskRes.data) setTask(taskRes.data);
      if (assetRes.code === 0 && assetRes.data) setAssets(assetRes.data.assets || []);
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

  async function handleAddAsset() {
    if (!uploadUrl.trim()) return;
    setUploading(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; assets: AssetItem[] } }>(
        `/api/video-tasks/${id}/assets`,
        {
          method: "POST",
          body: { assetKind: uploadKind, assetRole: uploadRole, source: "user_upload", url: uploadUrl.trim() },
        }
      );
      if (res.code === 0 && res.data) {
        setAssets(res.data.assets || []);
        setShowUpload(false);
        setUploadUrl("");
      } else {
        setError(res.message || "添加失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setUploading(false);
    }
  }

  async function handleUpdateRole(assetId: string, newRole: string) {
    try {
      await apiRequest(`/api/video-tasks/${id}/assets/${assetId}/role`, {
        method: "PATCH",
        body: { assetRole: newRole },
      });
      setEditingId(null);
      load();
    } catch (e: any) {
      setError(e.message || "更新失败");
    }
  }

  async function handleConfirm() {
    setConfirming(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data?: { status?: string } }>(
        `/api/video-tasks/${id}/assets/confirm`,
        { method: "POST", body: {} }
      );
      if (res.code === 0) {
        const nextStatus = res.data?.status;
        if (nextStatus === "reference_analyzing") {
          router.push(`/video-tasks/${id}/reference-analysis`);
        } else if (nextStatus === "plan_generating" || nextStatus === "waiting_plan_selection") {
          router.push(`/video-tasks/${id}/plans`);
        } else {
          load();
        }
      } else {
        setError(res.message || "确认失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setConfirming(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  const canUpload = task && ["asset_uploading", "waiting_asset_confirmation", "keyframe_configuring"].includes(task.status);
  const canConfirm = (task?.status === "asset_uploading" || task?.status === "waiting_asset_confirmation") && assets.length > 0;
  const isAnalyzing = task?.status === "asset_analyzing";
  const isReadOnly = task && !canUpload && !isAnalyzing;

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="mr-1 inline h-3 w-3" />返回工作台
          </Link>
          <h1 className="mt-2 text-2xl font-bold">素材管理</h1>
          <p className="text-sm text-muted-foreground">
            {isAnalyzing ? "AI 正在分析商品素材…" : "上传商品图片、模特参考、场景参考等素材"}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <CancelTaskButton taskId={id} />
          {canUpload && (
            <button
              onClick={() => setShowUpload(!showUpload)}
              className="inline-flex items-center gap-2 rounded-md border px-4 py-2 text-sm font-medium hover:bg-accent"
            >
              <Upload className="h-4 w-4" />添加素材
            </button>
          )}
          {canConfirm && (
            <button
              onClick={handleConfirm}
              disabled={confirming}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              <CheckCircle2 className="h-4 w-4" />
              {confirming ? "确认中..." : "确认并开始 AI 分析"}
            </button>
          )}
        </div>
      </div>

      {error && <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}

      {/* AI analyzing spinner */}
      {isAnalyzing && (
        <div className="flex flex-col items-center gap-4 rounded-lg border bg-card p-12">
          <Loader2 className="h-10 w-10 animate-spin text-primary" />
          <p className="text-lg font-medium">AI 正在分析你的商品素材</p>
          <p className="text-sm text-muted-foreground">分析商品属性、风格、材质、推荐拍摄角度…</p>
        </div>
      )}

      {/* Upload form */}
      {showUpload && (
        <div className="rounded-lg border bg-card p-6 space-y-4">
          <h3 className="font-semibold">添加素材</h3>
          <div className="grid grid-cols-3 gap-4">
            <div>
              <label className="text-sm font-medium">类型</label>
              <select value={uploadKind} onChange={(e) => setUploadKind(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm">
                <option value="image">图片</option>
                <option value="video">视频</option>
              </select>
            </div>
            <div>
              <label className="text-sm font-medium">用途</label>
              <select value={uploadRole} onChange={(e) => setUploadRole(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm">
                {Object.entries(ASSET_ROLE_LABELS).map(([k, v]) => (
                  <option key={k} value={k}>{v}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="text-sm font-medium">COS URL</label>
              <input type="text" value={uploadUrl} onChange={(e) => setUploadUrl(e.target.value)}
                placeholder="https://cos.example.com/..."
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm" />
            </div>
          </div>
          <div className="flex justify-end gap-3">
            <button onClick={() => setShowUpload(false)}
              className="rounded-md border px-4 py-2 text-sm hover:bg-accent">取消</button>
            <button onClick={handleAddAsset} disabled={uploading || !uploadUrl.trim()}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50">
              {uploading ? "添加中..." : "确认添加"}
            </button>
          </div>
        </div>
      )}

      {/* Asset grid */}
      {assets.length === 0 ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <Upload className="mx-auto h-8 w-8 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">还没有上传素材</p>
          {canUpload && <p className="mt-1 text-sm text-muted-foreground">点击「添加素材」上传商品图片</p>}
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-4">
          {assets.map((asset) => (
            <div key={asset.assetId} className={`rounded-lg border bg-card p-4 ${asset.confirmed ? "border-green-500/50" : ""}`}>
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-2">
                  {KIND_ICON[asset.assetKind] || <ImageIcon className="h-4 w-4" />}
                  {editingId === asset.assetId ? (
                    <select
                      defaultValue={asset.assetRole}
                      onChange={(e) => handleUpdateRole(asset.assetId, e.target.value)}
                      onBlur={() => setEditingId(null)}
                      autoFocus
                      className="rounded border px-2 py-0.5 text-xs"
                    >
                      {Object.entries(ASSET_ROLE_LABELS).map(([k, v]) => (
                        <option key={k} value={k}>{v}</option>
                      ))}
                    </select>
                  ) : (
                    <span className="inline-flex items-center rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                      {ASSET_ROLE_LABELS[asset.assetRole] || asset.assetRole}
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-2">
                  {asset.confirmed && <CheckCircle2 className="h-4 w-4 text-green-500" />}
                  {!isReadOnly && (
                    <button onClick={() => setEditingId(asset.assetId)}
                      className="text-muted-foreground hover:text-foreground">
                      <Pencil className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              </div>
              <img src={asset.url} alt={asset.assetRole}
                className="w-full h-32 object-cover rounded-md mb-2 bg-muted" />
              <p className="text-xs text-muted-foreground truncate">{asset.url}</p>
              {asset.fileName && <p className="text-xs text-muted-foreground">{asset.fileName}</p>}
            </div>
          ))}
        </div>
      )}

      {/* Read-only note */}
      {isReadOnly && (
        <div className="rounded-md bg-muted p-4 text-center text-sm text-muted-foreground">
          素材已确认，当前阶段不可编辑。
        </div>
      )}

      {/* Navigation hints */}
      {task?.status === "waiting_asset_confirmation" && (
        <div className="flex items-center gap-2 rounded-md border border-blue-500/50 bg-blue-50 p-4 text-sm text-blue-700">
          AI 分析已完成。如需调整素材，请点击上方「添加素材」。确认无误后点击素材列表中的确认按钮。
        </div>
      )}
    </div>
  );
}
