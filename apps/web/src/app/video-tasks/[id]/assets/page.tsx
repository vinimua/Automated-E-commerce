"use client";

import { apiRequest } from "@/lib/api-client";
import type { VideoTask, TaskMode, TaskAsset, TaskAssetListResponse, FashionAssetAnalysis } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState, useCallback, useRef } from "react";
import { ArrowLeft, Upload, Image as ImageIcon, Video, Music, Pencil, Trash2, CheckCircle2, Loader2 } from "lucide-react";
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

function isGeneratedImageAsset(asset: TaskAsset) {
  return asset.assetKind === "image" && (
    asset.source === "ai_generated" ||
    asset.assetRole === "generated_result" ||
    asset.assetRole === "image_variant"
  );
}

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
  const [creativePrompt, setCreativePrompt] = useState("");
  const [imagePrompt, setImagePrompt] = useState("");
  const [generatingImage, setGeneratingImage] = useState(false);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

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
      if (taskRes.code === 0 && taskRes.data) {
        setTask(taskRes.data);
      }
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
    if (migratedRef.current || !creativeState) return;
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
    (a) => a.assetRole?.startsWith("product_") || a.assetRole === "reference_video"
  );
  const userAddedAssets = assets.filter(
    (a) => !a.assetRole?.startsWith("product_") && a.assetRole !== "reference_video"
  );
  // Count product images. Include images with null/empty role (legacy DB records)
  // as well as explicit product_* and reference_video roles.
  const productImageCount = assets.filter((a) =>
    a.assetKind === "image" && (
      !a.assetRole ||
      a.assetRole.startsWith("product_") ||
      a.assetRole === "generated_result" ||
      a.assetRole === "image_variant"
    )
  ).length;
  const sourceImageAssets = assets.filter((a) => a.assetKind === "image" && a.url);

  const canUpload = task && ["asset_uploading", "waiting_asset_confirmation", "keyframe_configuring"].includes(task.status);
  const canDelete = task && ["asset_uploading", "waiting_asset_confirmation"].includes(task.status);
  const canStartAssetFlow = task?.status === "asset_uploading" || task?.status === "waiting_asset_confirmation";
  const canConfirm =
    canStartAssetFlow &&
    productImageCount >= modeConfig.minProductImages;
  const isAnalyzing = task?.status === "asset_analyzing";
  const assetAnalysis = task?.assetAnalysis as FashionAssetAnalysis | null | undefined;
  const isReadOnly = !!(task && !canUpload && !isAnalyzing);
  const isWaitingForPlanGeneration = task?.status === "waiting_asset_confirmation";
  const confirmButtonText = isWaitingForPlanGeneration
    ? "确认分析结果，生成视频方案"
    : modeConfig.confirmButtonText;

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
          body: {
            assetKind: uploadKind,
            assetRole: uploadRole,
            source: "user_upload",
            url: uploadUrl.trim(),
          },
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

  async function handleGenerateImage() {
    if (!imagePrompt.trim()) return;
    if (sourceImageAssets.length === 0) {
      setError("请先添加至少一张原始商品图，再生成/编辑新图。");
      return;
    }
    setGeneratingImage(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; assets: TaskAsset[] } }>(
        `/api/video-tasks/${id}/assets/generate-image`,
        {
          method: "POST",
          body: {
            prompt: imagePrompt.trim(),
            sourceAssetIds: sourceImageAssets.map((a) => a.assetId),
            assetRole: "image_variant",
          },
        }
      );
      if (res.code === 0 && res.data) {
        setAssets(res.data.assets || []);
        setImagePrompt("");
      } else {
        setError(res.message || "生成新图失败");
      }
    } catch (e: any) {
      setError(e.message || "生成新图失败");
    } finally {
      setGeneratingImage(false);
    }
  }

  async function handleRegenerateImage(asset: TaskAsset, feedback: string) {
    if (!feedback.trim()) return;
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string; data: { taskId: string; assets: TaskAsset[] } }>(
        `/api/video-tasks/${id}/assets/${asset.assetId}/regenerate-image`,
        {
          method: "POST",
          body: { feedback: feedback.trim() },
        }
      );
      if (res.code === 0 && res.data) {
        setAssets(res.data.assets || []);
      } else {
        setError(res.message || "重新生成失败");
      }
    } catch (e: any) {
      setError(e.message || "重新生成失败");
    }
  }

  async function handleUpdateRole(assetId: string, newRole: string) {
    try {
      await apiRequest(`/api/video-tasks/${id}/assets/${assetId}/role`, {
        method: "PATCH",
        body: { role: newRole },
      });
      setEditingId(null);
      load();
    } catch (e: any) {
      setError(e.message || "更新失败");
    }
  }

  async function handleDelete(assetId: string) {
    if (!confirm("确定要删除这个素材吗？")) return;
    try {
      await apiRequest<TaskAssetListResponse>(`/api/video-tasks/${id}/assets/${assetId}`, { method: "DELETE" });
      load();
    } catch (e: any) {
      setError(e.message || "删除失败");
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
      const latestGeneratedImage = [...assets]
        .filter(isGeneratedImageAsset)
        .sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime())[0];
      const confirmAssetIds = latestGeneratedImage
        ? [
            latestGeneratedImage.assetId,
            ...assets
              .filter((a) => a.assetRole === "reference_video")
              .map((a) => a.assetId),
          ]
        : undefined;
      const body: Record<string, unknown> = {};
      if (confirmAssetIds) body.assetIds = confirmAssetIds;
      if (creativePrompt.trim()) body.creativePrompt = creativePrompt.trim();

      const res = await apiRequest<{ code: number; message: string; data?: { status?: string } }>(
        `/api/video-tasks/${id}/assets/confirm`,
        { method: "POST", body }
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
          {canStartAssetFlow && (
            <button
              onClick={handleConfirm}
              disabled={confirming || !canConfirm}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              title={!canConfirm ? `请至少上传 ${modeConfig.minProductImages} 张用途为商品正面、商品背面或商品细节的图片` : undefined}
            >
              <CheckCircle2 className="h-4 w-4" />
              {confirming ? "确认中..." : confirmButtonText}
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

      {canUpload && (
        <div className="rounded-lg border bg-card p-6 space-y-4">
          <div>
            <h2 className="text-lg font-semibold">生成/编辑新商品图</h2>
            <p className="text-sm text-muted-foreground">
              先基于当前素材生成一张新图。生成结果会作为未确认素材加入下方列表，确认后才会进入 AI 素材分析。
            </p>
          </div>
          <textarea
            value={imagePrompt}
            onChange={(e) => setImagePrompt(e.target.value)}
            placeholder="例如：在原有黑色 T 恤背面加一个大面积蝴蝶图案，保持版型、主色和正面小图案不变。"
            rows={3}
            className="w-full rounded-md border px-3 py-2 text-sm placeholder:text-muted-foreground/50 resize-vertical"
          />
          <div className="flex items-center justify-between gap-3">
            <p className="text-xs text-muted-foreground">
              将参考当前 {sourceImageAssets.length} 张图片素材生成新图。
            </p>
            <button
              onClick={handleGenerateImage}
              disabled={generatingImage || !imagePrompt.trim() || sourceImageAssets.length === 0}
              className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {generatingImage && <Loader2 className="h-4 w-4 animate-spin" />}
              {generatingImage ? "生成中..." : "生成/编辑新图"}
            </button>
          </div>
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
                canDelete={!!canDelete}
                editingId={editingId}
                onEditRole={setEditingId}
                onUpdateRole={handleUpdateRole}
                onDelete={handleDelete}
                onPreview={setPreviewUrl}
                onRegenerate={handleRegenerateImage}
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
                  canDelete={!!canDelete}
                  editingId={editingId}
                  onEditRole={setEditingId}
                  onUpdateRole={handleUpdateRole}
                  onDelete={handleDelete}
                  onPreview={setPreviewUrl}
                  onRegenerate={handleRegenerateImage}
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

      {/* Creative direction input */}
      {canStartAssetFlow && (
        <div className="rounded-lg border bg-card p-6 space-y-3">
          <div>
            <h3 className="font-semibold">创意方向（选填）</h3>
            <p className="text-sm text-muted-foreground">
              看完素材后你有什么想法？AI 会在分析时优先考虑你的创意方向。例如：想突出的卖点、风格偏好、目标受众、需要避免的内容等。
            </p>
          </div>
          <textarea
            value={creativePrompt}
            onChange={(e) => setCreativePrompt(e.target.value)}
            placeholder="例如：我想突出背面的渐变印花和 oversized 版型，风格偏街头运动，目标 18-25 岁女生，别用咖啡厅场景…"
            rows={3}
            className="w-full rounded-md border px-3 py-2 text-sm placeholder:text-muted-foreground/50 resize-vertical"
          />
        </div>
      )}

      {/* Product image requirement hint */}
      {canUpload && !canConfirm && (
        <div className="flex items-center gap-2 rounded-md border border-amber-500/50 bg-amber-50 p-4 text-sm text-amber-700">
          请至少上传 {modeConfig.minProductImages} 张用途为「商品正面 / 商品背面 / 商品细节」的图片，才能确认并开始 AI 分析。
        </div>
      )}

      {/* AI analysis results */}
      {task?.status === "waiting_asset_confirmation" && (
        <div className="rounded-lg border border-blue-500/50 bg-blue-50 p-6 space-y-3">
          <div className="flex items-center gap-2 text-blue-700 font-semibold">
            <CheckCircle2 className="h-5 w-5" />AI 分析完成
          </div>
          {assetAnalysis?.analysisText ? (
            <>
              <div className="whitespace-pre-wrap rounded-md border border-blue-200 bg-white/70 p-4 text-sm leading-7 text-slate-800">
                {assetAnalysis.analysisText}
              </div>
              <div className="flex flex-wrap gap-x-5 gap-y-1 text-xs text-blue-700/70">
                <span>模型：{assetAnalysis.model}</span>
                <span>已分析素材：{assetAnalysis.analyzedAssetIds.length} 个</span>
                <span>分析时间：{new Date(assetAnalysis.analyzedAt).toLocaleString()}</span>
              </div>
            </>
          ) : (
            <p className="text-sm text-blue-600">分析结果暂未返回，请稍后刷新。</p>
          )}
        </div>
      )}

      {/* Image preview modal */}
      {previewUrl && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 cursor-pointer"
          onClick={() => setPreviewUrl(null)}
        >
          <img
            src={previewUrl}
            alt="预览"
            className="max-h-[90vh] max-w-[90vw] rounded-lg object-contain"
            onClick={(e) => e.stopPropagation()}
          />
        </div>
      )}
    </div>
  );
}

// ── Asset Card Sub-component ──
function AssetCard({
  asset,
  isReadOnly,
  canDelete,
  editingId,
  onEditRole,
  onUpdateRole,
  onDelete,
  onPreview,
  onRegenerate,
}: {
  asset: TaskAsset;
  isReadOnly: boolean;
  canDelete: boolean;
  editingId: string | null;
  onEditRole: (id: string | null) => void;
  onUpdateRole: (assetId: string, newRole: string) => void;
  onDelete: (assetId: string) => void;
  onPreview?: (url: string) => void;
  onRegenerate?: (asset: TaskAsset, feedback: string) => Promise<void>;
}) {
  const [feedback, setFeedback] = useState("");
  const [regenerating, setRegenerating] = useState(false);
  const canRegenerate = !isReadOnly && isGeneratedImageAsset(asset) && !!onRegenerate;

  async function submitRegeneration() {
    if (!feedback.trim() || !onRegenerate) return;
    setRegenerating(true);
    try {
      await onRegenerate(asset, feedback.trim());
      setFeedback("");
    } finally {
      setRegenerating(false);
    }
  }

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
            <>
              <button onClick={() => onEditRole(asset.assetId)}
                className="text-muted-foreground hover:text-foreground">
                <Pencil className="h-3.5 w-3.5" />
              </button>
              {canDelete && (
                <button onClick={() => onDelete(asset.assetId)}
                  className="text-muted-foreground hover:text-destructive">
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              )}
            </>
          )}
        </div>
      </div>
      <img
        src={asset.url} alt={asset.assetRole}
        className="w-full h-32 object-cover rounded-md mb-2 bg-muted cursor-pointer hover:opacity-90 transition-opacity"
        onClick={() => onPreview?.(asset.url)}
      />
      <p className="text-xs text-muted-foreground truncate">{asset.url}</p>
      {asset.fileName && <p className="text-xs text-muted-foreground">{asset.fileName}</p>}
      {canRegenerate && (
        <div className="mt-3 space-y-2 rounded-md border bg-muted/30 p-3">
          <label className="text-xs font-medium">不满意？告诉 AI 这张图要怎么改</label>
          <textarea
            value={feedback}
            onChange={(e) => setFeedback(e.target.value)}
            placeholder="例如：图案再小一点，放到胸前左侧；衣服版型和颜色不要变"
            rows={2}
            className="w-full rounded-md border bg-background px-2 py-1.5 text-xs"
          />
          <button
            onClick={submitRegeneration}
            disabled={regenerating || !feedback.trim()}
            className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            {regenerating && <Loader2 className="h-3 w-3 animate-spin" />}
            {regenerating ? "重新生成中..." : "按反馈重新生成"}
          </button>
        </div>
      )}
    </div>
  );
}
