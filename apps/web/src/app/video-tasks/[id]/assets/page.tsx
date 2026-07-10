"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask, TaskMode, TaskAsset } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useCallback, useRef } from "react";
import { ArrowLeft, Upload, Image as ImageIcon, Video, Music, Pencil, CheckCircle2, Loader2 } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

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

// ── Mode-specific configuration ──
const TASK_MODE_CONFIG: Record<TaskMode, {
  pageTitle: string;
  pageDescription: string;
  confirmButtonText: string;
  minProductImages: number;
}> = {
  PRODUCT_CREATIVE: {
    pageTitle: "确认商品素材",
    pageDescription: "AI 会根据商品图片和商品描述生成视频创意。请确认至少有 1 张商品图片，建议补充正面、背面、细节图。",
    confirmButtonText: "确认商品素材并开始 AI 分析",
    minProductImages: 1,
  },
  REFERENCE_STORYBOARD: {
    pageTitle: "确认参考视频与商品素材",
    pageDescription: "系统已带入你在创建页填写的参考视频。请补充至少 1 张商品图。确认后，AI 会先拆解参考视频分镜。",
    confirmButtonText: "确认素材并分析参考视频",
    minProductImages: 1,
  },
  USER_SCRIPT: {
    pageTitle: "确认脚本与商品素材",
    pageDescription: "系统已保存你提供的脚本。请补充至少 1 张商品图，便于后续生成关键帧和视频片段。",
    confirmButtonText: "确认素材并生成视频方案",
    minProductImages: 1,
  },
  CUSTOM_STORYBOARD: {
    pageTitle: "确认分镜与商品素材",
    pageDescription: "系统已保存你提供的分镜结构。请补充至少 1 张商品图，便于 AI 补齐画面提示词和关键帧。",
    confirmButtonText: "确认素材并生成视频方案",
    minProductImages: 1,
  },
};

export default function AssetsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [task, setTask] = useState<VideoTask | null>(null);
  const [assets, setAssets] = useState<TaskAsset[]>([]);
  const [creativeState, setCreativeState] = useState<Record<string, unknown> | null>(null);
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

  // One-shot migration flag
  const migratedRef = useRef(false);

  const load = useCallback(async () => {
    try {
      const [taskRes, assetRes, csRes] = await Promise.all([
        apiRequest<{ code: number; message: string; data: VideoTask }>(`/api/video-tasks/${id}`),
        apiRequest<{ code: number; message: string; data: { taskId: string; assets: TaskAsset[] } }>(
          `/api/video-tasks/${id}/assets`
        ),
        apiRequest<{ code: number; message: string; data: Record<string, unknown> }>(
          `/api/video-tasks/${id}/creative-state`
        ).catch(() => ({ code: 0, message: "ok", data: null })),
      ]);
      if (taskRes.code === 0 && taskRes.data) setTask(taskRes.data);
      if (assetRes.code === 0 && assetRes.data) setAssets(assetRes.data.assets || []);
      if (csRes.code === 0 && csRes.data) setCreativeState(csRes.data);
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

  // One-shot migration: ensure product images from creative_state exist in task_assets
  useEffect(() => {
    if (migratedRef.current || !creativeState || assets.length === 0) return;
    const csProductData = creativeState.product as { imageUrls?: string[] } | null | undefined;
    const csImageUrls = csProductData?.imageUrls || [];
    if (csImageUrls.length === 0) return;
    const assetUrls = new Set(assets.map((a) => a.url));
    const missing = csImageUrls.filter((url) => !assetUrls.has(url));
    if (missing.length === 0) return;

    migratedRef.current = true;
    (async () => {
      for (const url of missing) {
        try {
          await apiRequest(`/api/video-tasks/${id}/assets`, {
            method: "POST",
            body: {
              assetKind: "image",
              assetRole: "product_front",
              source: "user_upload",
              url,
              description: "Auto-migrated from product images",
            },
          });
        } catch { /* best-effort migration */ }
      }
      load();
    })();
  }, [creativeState, assets, id, load]);

  // ── Derived values ──
  const taskMode: TaskMode = task?.taskMode || "PRODUCT_CREATIVE";
  const modeConfig = TASK_MODE_CONFIG[taskMode];

  const csProduct = creativeState?.product as { name?: string; description?: string; targetMarket?: string; imageUrls?: string[] } | null | undefined;
  const csRefVideo = creativeState?.referenceVideo as { url?: string } | null | undefined;
  const csRequirements = creativeState?.userRequirements as { scriptText?: string; storyboardText?: string } | null | undefined;

  const prePopulatedAssets = assets.filter(
    (a) => a.assetRole.startsWith("product_") || a.assetRole === "reference_video"
  );
  const userAddedAssets = assets.filter(
    (a) => !a.assetRole.startsWith("product_") && a.assetRole !== "reference_video"
  );
  const productImageCount = assets.filter((a) => a.assetRole.startsWith("product_")).length;

  const canUpload = task && ["asset_uploading", "waiting_asset_confirmation", "keyframe_configuring"].includes(task.status);
  const canConfirm =
    (task?.status === "asset_uploading" || task?.status === "waiting_asset_confirmation") &&
    productImageCount >= modeConfig.minProductImages;
  const isAnalyzing = task?.status === "asset_analyzing";
  const isReadOnly = !!(task && !canUpload && !isAnalyzing);

  // ── Handlers ──
  async function handleAddAsset() {
    if (!uploadUrl.trim()) return;
    setUploading(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; assets: TaskAsset[] } }>(
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
    if (productImageCount < modeConfig.minProductImages) {
      setError(`请至少上传 ${modeConfig.minProductImages} 张商品图片后再确认`);
      return;
    }
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

  // ── Loading state ──
  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="mr-1 inline h-3 w-3" />返回工作台
          </Link>
          <h1 className="mt-2 text-2xl font-bold">{modeConfig.pageTitle}</h1>
          <p className="text-sm text-muted-foreground">
            {isAnalyzing ? "AI 正在分析商品素材…" : modeConfig.pageDescription}
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
              {confirming ? "确认中..." : modeConfig.confirmButtonText}
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

      {/* ── Zone 1: Task Summary (from creative_state) ── */}
      {creativeState && (
        <div className="rounded-lg border bg-card p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">任务摘要</h2>
            <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">
              {taskMode === "PRODUCT_CREATIVE" ? "商品创意" :
               taskMode === "REFERENCE_STORYBOARD" ? "参考视频" :
               taskMode === "USER_SCRIPT" ? "用户脚本" : "用户分镜"}
            </span>
          </div>

          {/* Product info */}
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <span className="text-muted-foreground">商品名称</span>
              <p className="font-medium">{csProduct?.name || "—"}</p>
            </div>
            <div>
              <span className="text-muted-foreground">目标市场</span>
              <p className="font-medium">{csProduct?.targetMarket || "—"}</p>
            </div>
          </div>

          {csProduct?.description && (
            <div className="text-sm">
              <span className="text-muted-foreground">商品描述</span>
              <p className="mt-0.5">{csProduct.description.slice(0, 200)}</p>
            </div>
          )}

          {/* Product images from creative_state */}
          {csProduct?.imageUrls && csProduct.imageUrls.length > 0 && (
            <div>
              <span className="text-sm text-muted-foreground">
                商品图片 ({csProduct.imageUrls.length})
              </span>
              <div className="mt-1 flex gap-2 flex-wrap">
                {csProduct.imageUrls.map((url, i) => (
                  <img key={i} src={url} alt="" className="h-16 w-16 rounded border object-cover bg-muted" />
                ))}
              </div>
            </div>
          )}

          {/* Mode-specific summary */}
          {taskMode === "REFERENCE_STORYBOARD" && csRefVideo?.url && (
            <div className="text-sm">
              <span className="text-muted-foreground">参考视频</span>
              <p className="truncate font-mono text-xs mt-0.5">{csRefVideo.url}</p>
            </div>
          )}

          {taskMode === "USER_SCRIPT" && csRequirements?.scriptText && (
            <div className="text-sm">
              <span className="text-muted-foreground">脚本摘要</span>
              <p className="mt-0.5 text-xs text-muted-foreground line-clamp-3">
                {csRequirements.scriptText.slice(0, 200)}
              </p>
            </div>
          )}

          {taskMode === "CUSTOM_STORYBOARD" && csRequirements?.storyboardText && (
            <div className="text-sm">
              <span className="text-muted-foreground">分镜摘要</span>
              <p className="mt-0.5 text-xs text-muted-foreground line-clamp-3">
                {csRequirements.storyboardText.slice(0, 200)}
              </p>
            </div>
          )}
        </div>
      )}

      {/* Empty state */}
      {assets.length === 0 && !isAnalyzing && (
        <div className="rounded-lg border bg-card p-12 text-center">
          <Upload className="mx-auto h-8 w-8 text-muted-foreground" />
          <p className="mt-4 text-muted-foreground">还没有上传素材</p>
          {canUpload && <p className="mt-1 text-sm text-muted-foreground">点击「添加素材」上传商品图片</p>}
        </div>
      )}

      {/* ── Zone 2: Pre-populated Assets ── */}
      {prePopulatedAssets.length > 0 && (
        <div className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold">已带入素材</h2>
            <p className="text-sm text-muted-foreground">创建任务时自动添加的素材</p>
          </div>
          <div className="grid grid-cols-2 gap-4">
            {prePopulatedAssets.map((asset) => (
              <AssetCard
                key={asset.assetId}
                asset={asset}
                isReadOnly={isReadOnly}
                editingId={editingId}
                onEditRole={setEditingId}
                onUpdateRole={handleUpdateRole}
              />
            ))}
          </div>
        </div>
      )}

      {/* ── Zone 3: Add More Assets ── */}
      {(canUpload || userAddedAssets.length > 0 || showUpload) && (
        <div className="space-y-4">
          <div>
            <h2 className="text-lg font-semibold">补充素材</h2>
            <p className="text-sm text-muted-foreground">
              添加更多商品图片、模特参考、场景参考等
            </p>
          </div>

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

          {/* User-added asset grid */}
          {userAddedAssets.length > 0 && (
            <div className="grid grid-cols-2 gap-4">
              {userAddedAssets.map((asset) => (
                <AssetCard
                  key={asset.assetId}
                  asset={asset}
                  isReadOnly={isReadOnly}
                  editingId={editingId}
                  onEditRole={setEditingId}
                  onUpdateRole={handleUpdateRole}
                />
              ))}
            </div>
          )}
        </div>
      )}

      {/* Read-only note */}
      {isReadOnly && (
        <div className="rounded-md bg-muted p-4 text-center text-sm text-muted-foreground">
          素材已确认，当前阶段不可编辑。
        </div>
      )}

      {/* Product image requirement hint */}
      {canUpload && !canConfirm && (
        <div className="flex items-center gap-2 rounded-md border border-amber-500/50 bg-amber-50 p-4 text-sm text-amber-700">
          请至少上传 {modeConfig.minProductImages} 张商品图片（商品正面/背面/细节），才能确认并开始 AI 分析。
        </div>
      )}

      {/* AI analysis complete hint */}
      {task?.status === "waiting_asset_confirmation" && (
        <div className="flex items-center gap-2 rounded-md border border-blue-500/50 bg-blue-50 p-4 text-sm text-blue-700">
          AI 分析已完成。如需调整素材，请点击上方「添加素材」。确认无误后点击确认按钮。
        </div>
      )}
    </div>
  );
}

// ── Asset Card Sub-component ──
function AssetCard({
  asset,
  isReadOnly,
  editingId,
  onEditRole,
  onUpdateRole,
}: {
  asset: TaskAsset;
  isReadOnly: boolean;
  editingId: string | null;
  onEditRole: (id: string | null) => void;
  onUpdateRole: (assetId: string, newRole: string) => void;
}) {
  return (
    <div className={`rounded-lg border bg-card p-4 ${asset.confirmed ? "border-green-500/50" : ""}`}>
      <div className="flex items-start justify-between mb-3">
        <div className="flex items-center gap-2">
          {KIND_ICON[asset.assetKind] || <ImageIcon className="h-4 w-4" />}
          {editingId === asset.assetId ? (
            <select
              defaultValue={asset.assetRole}
              onChange={(e) => onUpdateRole(asset.assetId, e.target.value)}
              onBlur={() => onEditRole(null)}
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
            <button onClick={() => onEditRole(asset.assetId)}
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
  );
}
