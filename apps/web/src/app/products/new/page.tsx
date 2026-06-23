"use client";

import { apiRequest } from "@/lib/api-client";
import { V1_VIDEO_TYPES, V1_DURATIONS, VIDEO_TYPE_LABELS } from "@/types/api";
import type { CreateProductData, CreateVideoTaskData, VideoType } from "@/types/api";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function NewProductPage() {
  const router = useRouter();
  const [step, setStep] = useState<"product" | "task">("product");

  // Product form
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [productLink, setProductLink] = useState("");
  const [targetMarket, setTargetMarket] = useState("US");
  const [language, setLanguage] = useState("en");
  const [imageUrl, setImageUrl] = useState("");
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [productError, setProductError] = useState("");
  const [productLoading, setProductLoading] = useState(false);
  const [productId, setProductId] = useState("");

  // Task form
  const [duration, setDuration] = useState<number>(20);
  const [videoType, setVideoType] = useState<VideoType>("pain_point_solution");
  const [needSubtitles, setNeedSubtitles] = useState(true);
  const [taskError, setTaskError] = useState("");
  const [taskLoading, setTaskLoading] = useState(false);

  function addImage() {
    if (!imageUrl) return;
    if (!imageUrl.startsWith("http")) { setProductError("请输入有效的图片 URL"); return; }
    setImageUrls([...imageUrls, imageUrl]);
    setImageUrl("");
    setProductError("");
  }

  async function createProduct(e: React.FormEvent) {
    e.preventDefault();
    setProductError("");
    if (!name) { setProductError("请输入商品名称"); return; }
    if (imageUrls.length === 0) { setProductError("请至少添加一张商品图片"); return; }

    setProductLoading(true);
    try {
      const res = await apiRequest<{ code: number; message: string; data: CreateProductData }>("/api/products", {
        method: "POST",
        body: { name, description, productLink, imageUrls, targetMarket, language },
      });
      if (res.code === 0) {
        setProductId(res.data.productId);
        setStep("task");
      } else {
        setProductError(res.message || "创建商品失败");
      }
    } catch (e: any) {
      setProductError(e.message || "网络错误");
    } finally {
      setProductLoading(false);
    }
  }

  async function createTask(e: React.FormEvent) {
    e.preventDefault();
    setTaskError("");
    setTaskLoading(true);
    try {
      const res = await apiRequest<{ code: number; message: string; data: CreateVideoTaskData }>("/api/video-tasks", {
        method: "POST",
        body: { productId, duration, videoType, needSubtitles },
      });
      if (res.code === 0) {
        router.push(`/video-tasks/${res.data.taskId}/plans`);
      } else {
        setTaskError(res.message || "创建任务失败");
      }
    } catch (e: any) {
      setTaskError(e.message || "网络错误");
    } finally {
      setTaskLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-8">
      <div>
        <h1 className="text-2xl font-bold">新建视频</h1>
        <p className="text-sm text-muted-foreground">
          {step === "product" ? "第一步：填写商品信息" : "第二步：选择视频配置"}
        </p>
        {/* Step indicator */}
        <div className="mt-4 flex gap-4">
          <div className={`flex items-center gap-2 text-sm ${step === "product" ? "font-medium text-primary" : "text-muted-foreground"}`}>
            <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${step === "product" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>1</span>
            商品信息
          </div>
          <div className={`flex items-center gap-2 text-sm ${step === "task" ? "font-medium text-primary" : "text-muted-foreground"}`}>
            <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${step === "task" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>2</span>
            视频配置
          </div>
        </div>
      </div>

      {/* Step 1: Product form */}
      {step === "product" && (
        <form onSubmit={createProduct} className="space-y-4">
          <div>
            <label className="text-sm font-medium">商品名称 *</label>
            <input type="text" value={name} onChange={(e) => setName(e.target.value)}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="如：Wireless Earbuds Pro" />
          </div>
          <div>
            <label className="text-sm font-medium">商品描述</label>
            <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="描述商品的主要功能和卖点..." />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium">目标市场</label>
              <select value={targetMarket} onChange={(e) => setTargetMarket(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none">
                <option value="US">美国 (US)</option>
                <option value="UK">英国 (UK)</option>
                <option value="JP">日本 (JP)</option>
              </select>
            </div>
            <div>
              <label className="text-sm font-medium">语言</label>
              <select value={language} onChange={(e) => setLanguage(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none">
                <option value="en">English</option>
                <option value="zh">中文</option>
                <option value="ja">日本語</option>
              </select>
            </div>
          </div>
          <div>
            <label className="text-sm font-medium">商品链接</label>
            <input type="url" value={productLink} onChange={(e) => setProductLink(e.target.value)}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="https://..." />
          </div>

          {/* Image URLs */}
          <div>
            <label className="text-sm font-medium">商品图片 *</label>
            <div className="mt-1 flex gap-2">
              <input type="url" value={imageUrl} onChange={(e) => setImageUrl(e.target.value)}
                className="flex-1 rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="输入图片 URL" onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addImage(); } }} />
              <button type="button" onClick={addImage}
                className="rounded-md border px-3 py-2 text-sm hover:bg-accent">添加</button>
            </div>
            {imageUrls.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-2">
                {imageUrls.map((url, i) => (
                  <div key={i} className="relative group">
                    <img src={url} alt={`Product image ${i + 1}`} className="h-20 w-20 rounded-md border object-cover" />
                    <button
                      type="button" onClick={() => setImageUrls(imageUrls.filter((_, j) => j !== i))}
                      className="absolute -right-1 -top-1 hidden h-5 w-5 items-center justify-center rounded-full bg-destructive text-xs text-white group-hover:flex"
                    >×</button>
                    {i === 0 && <span className="absolute bottom-0 left-0 rounded-tr bg-primary px-1 text-[10px] text-primary-foreground">主图</span>}
                  </div>
                ))}
              </div>
            )}
          </div>

          {productError && <p className="text-sm text-destructive">{productError}</p>}

          <button type="submit" disabled={productLoading}
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50">
            {productLoading ? "创建中..." : "下一步：视频配置"}
          </button>
        </form>
      )}

      {/* Step 2: Task form */}
      {step === "task" && (
        <form onSubmit={createTask} className="space-y-4">
          <div>
            <label className="text-sm font-medium">视频类型 *</label>
            <div className="mt-2 grid grid-cols-1 gap-3">
              {V1_VIDEO_TYPES.map((vt) => (
                <label key={vt}
                  className={`flex cursor-pointer items-center gap-3 rounded-lg border p-4 transition-colors ${
                    videoType === vt ? "border-primary bg-primary/5 ring-1 ring-primary" : "hover:bg-accent"
                  }`}
                >
                  <input type="radio" name="videoType" value={vt} checked={videoType === vt}
                    onChange={(e) => setVideoType(e.target.value as VideoType)} className="h-4 w-4" />
                  <div>
                    <p className="font-medium">{VIDEO_TYPE_LABELS[vt]}</p>
                    <p className="text-xs text-muted-foreground">
                      {vt === "pain_point_solution" && "痛点 → 产品 → 解决方案 → 效果 → CTA"}
                      {vt === "before_after" && "结果预告 → 使用前 → 过程 → 对比 → 产品特写"}
                      {vt === "review" && "问题 → 产品 → 测试 → 结果 → 推荐"}
                    </p>
                  </div>
                </label>
              ))}
            </div>
          </div>

          <div>
            <label className="text-sm font-medium">视频时长</label>
            <div className="mt-2 flex gap-3">
              {V1_DURATIONS.map((d) => (
                <button type="button" key={d}
                  onClick={() => setDuration(d)}
                  className={`rounded-lg border px-4 py-2 text-sm font-medium transition-colors ${
                    duration === d ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent"
                  }`}
                >{d}s</button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input type="checkbox" id="subtitles" checked={needSubtitles}
              onChange={(e) => setNeedSubtitles(e.target.checked)} className="h-4 w-4" />
            <label htmlFor="subtitles" className="text-sm">生成英文字幕</label>
          </div>

          {taskError && <p className="text-sm text-destructive">{taskError}</p>}

          <div className="flex gap-3">
            <button type="button" onClick={() => setStep("product")}
              className="rounded-md border px-4 py-2 text-sm hover:bg-accent">上一步</button>
            <button type="submit" disabled={taskLoading}
              className="flex-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50">
              {taskLoading ? "创建中..." : "创建任务 → AI 分析"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
