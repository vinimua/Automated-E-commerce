"use client";

import { apiRequest } from "@/lib/api-client";
import { V1_DURATIONS } from "@/types/api";
import type { CreateProductData, CreateVideoTaskData, TaskMode } from "@/types/api";
import { useRouter } from "next/navigation";
import { useState } from "react";

type Step = "mode" | "details";

const TASK_MODES: Array<{ value: TaskMode; title: string; desc: string; primaryInput: string }> = [
  {
    value: "PRODUCT_CREATIVE",
    title: "AI 根据商品生成创意",
    desc: "从商品图和卖点出发，生成差异化创意方案。",
    primaryInput: "商品图片",
  },
  {
    value: "REFERENCE_STORYBOARD",
    title: "按参考视频分镜生成",
    desc: "先上传参考视频，AI 拆解分镜，再替换成你的商品内容。",
    primaryInput: "参考视频",
  },
  {
    value: "USER_SCRIPT",
    title: "我有脚本",
    desc: "用户提供脚本，AI 拆解分镜、关键帧和视频片段。",
    primaryInput: "脚本",
  },
  {
    value: "CUSTOM_STORYBOARD",
    title: "我有分镜",
    desc: "用户提供分镜结构，AI 补齐画面与片段生成。",
    primaryInput: "分镜",
  },
];

export default function NewProductPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("mode");

  const [taskMode, setTaskMode] = useState<TaskMode>("PRODUCT_CREATIVE");
  const [duration, setDuration] = useState<number>(20);
  const [needSubtitles, setNeedSubtitles] = useState(true);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [productLink, setProductLink] = useState("");
  const [targetMarket, setTargetMarket] = useState("US");
  const [language, setLanguage] = useState("en");

  const [imageUrl, setImageUrl] = useState("");
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [referenceVideoUrl, setReferenceVideoUrl] = useState("");
  const [scriptText, setScriptText] = useState("");
  const [storyboardText, setStoryboardText] = useState("");

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const selectedMode = TASK_MODES.find((m) => m.value === taskMode) ?? TASK_MODES[0];

  function addImage() {
    const url = imageUrl.trim();
    if (!url) return;
    if (!url.startsWith("http")) {
      setError("请输入有效的图片 URL");
      return;
    }
    setImageUrls([...imageUrls, url]);
    setImageUrl("");
    setError("");
  }

  function validateDetails() {
    if (!name.trim()) {
      return "请输入商品名称";
    }
    if (taskMode === "PRODUCT_CREATIVE" && imageUrls.length === 0) {
      return "商品创意模式需要至少一张商品图片";
    }
    if (taskMode === "REFERENCE_STORYBOARD" && !referenceVideoUrl.trim().startsWith("http")) {
      return "请先填写有效的参考视频 URL";
    }
    if (taskMode === "USER_SCRIPT" && !scriptText.trim()) {
      return "请填写脚本内容";
    }
    if (taskMode === "CUSTOM_STORYBOARD" && !storyboardText.trim()) {
      return "请填写分镜内容";
    }
    return "";
  }

  async function saveInitialContext(taskId: string) {
    const userRequirements: Record<string, unknown> = {
      taskMode,
      duration,
      needSubtitles,
      description: description.trim(),
    };

    if (taskMode === "REFERENCE_STORYBOARD") {
      userRequirements.referenceVideoUrl = referenceVideoUrl.trim();
      await apiRequest(`/api/video-tasks/${taskId}/assets`, {
        method: "POST",
        body: {
          assetKind: "video",
          assetRole: "reference_video",
          source: "user_upload",
          url: referenceVideoUrl.trim(),
          description: "Initial reference video uploaded during task creation",
        },
      });
    }

    // Write product images to task_assets for all modes
    if (imageUrls.length > 0) {
      for (const url of imageUrls) {
        await apiRequest(`/api/video-tasks/${taskId}/assets`, {
          method: "POST",
          body: {
            assetKind: "image",
            assetRole: "product_front",
            source: "user_upload",
            url,
            description: "Product image uploaded during task creation",
          },
        });
      }
    }

    if (taskMode === "USER_SCRIPT") {
      userRequirements.scriptText = scriptText.trim();
    }

    if (taskMode === "CUSTOM_STORYBOARD") {
      userRequirements.storyboardText = storyboardText.trim();
    }

    await apiRequest(`/api/video-tasks/${taskId}/creative-state`, {
      method: "PATCH",
      body: {
        product: {
          name: name.trim(),
          description: description.trim(),
          productLink: productLink.trim(),
          imageUrls,
          targetMarket,
          language,
        },
        referenceVideo:
          taskMode === "REFERENCE_STORYBOARD"
            ? {
                url: referenceVideoUrl.trim(),
                source: "user_upload",
              }
            : undefined,
        userRequirements,
      },
    });
  }

  async function createTask(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    const validationError = validateDetails();
    if (validationError) {
      setError(validationError);
      return;
    }

    setLoading(true);
    try {
      const productRes = await apiRequest<{ code: number; message: string; data: CreateProductData }>("/api/products", {
        method: "POST",
        body: {
          name: name.trim(),
          description,
          productLink,
          imageUrls,
          targetMarket,
          language,
        },
      });

      if (productRes.code !== 0) {
        setError(productRes.message || "创建商品失败");
        return;
      }

      const taskRes = await apiRequest<{ code: number; message: string; data: CreateVideoTaskData }>("/api/video-tasks", {
        method: "POST",
        body: {
          productId: productRes.data.productId,
          duration,
          needSubtitles,
          taskMode,
        },
      });

      if (taskRes.code !== 0) {
        setError(taskRes.message || "创建任务失败");
        return;
      }

      await saveInitialContext(taskRes.data.taskId);

      const status = taskRes.data.status;
      if (status === "asset_uploading" || status === "asset_analyzing" || status === "waiting_asset_confirmation") {
        router.push(`/video-tasks/${taskRes.data.taskId}/assets`);
      } else {
        router.push(`/video-tasks/${taskRes.data.taskId}/plans`);
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-8">
      <div>
        <h1 className="text-2xl font-bold">新建视频</h1>
        <p className="text-sm text-muted-foreground">
          {step === "mode" ? "第一步：选择创作方式" : "第二步：填写最小创建信息"}
        </p>

        <div className="mt-4 flex gap-4">
          <div className={`flex items-center gap-2 text-sm ${step === "mode" ? "font-medium text-primary" : "text-muted-foreground"}`}>
            <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${step === "mode" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>1</span>
            创作方式
          </div>
          <div className={`flex items-center gap-2 text-sm ${step === "details" ? "font-medium text-primary" : "text-muted-foreground"}`}>
            <span className={`flex h-6 w-6 items-center justify-center rounded-full text-xs ${step === "details" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>2</span>
            最小创建信息
          </div>
        </div>
      </div>

      {step === "mode" && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 gap-3">
            {TASK_MODES.map((mode) => (
              <label
                key={mode.value}
                className={`flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors ${
                  taskMode === mode.value ? "border-primary bg-primary/5 ring-1 ring-primary" : "hover:bg-accent"
                }`}
              >
                <input
                  type="radio"
                  name="taskMode"
                  value={mode.value}
                  checked={taskMode === mode.value}
                  onChange={(e) => setTaskMode(e.target.value as TaskMode)}
                  className="mt-1 h-4 w-4"
                />
                <span className="space-y-1">
                  <span className="block text-sm font-medium">{mode.title}</span>
                  <span className="block text-xs text-muted-foreground">{mode.desc}</span>
                </span>
              </label>
            ))}
          </div>

          <button
            type="button"
            onClick={() => setStep("details")}
            className="w-full rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
          >
            下一步：填写最小创建信息
          </button>
        </div>
      )}

      {step === "details" && (
        <form onSubmit={createTask} className="space-y-4">
          {taskMode === "REFERENCE_STORYBOARD" && (
            <div>
              <label className="text-sm font-medium">参考视频 URL *</label>
              <input
                type="url"
                value={referenceVideoUrl}
                onChange={(e) => setReferenceVideoUrl(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="https://..."
              />
            </div>
          )}

          {taskMode === "USER_SCRIPT" && (
            <div>
              <label className="text-sm font-medium">脚本内容 *</label>
              <textarea
                value={scriptText}
                onChange={(e) => setScriptText(e.target.value)}
                rows={5}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="粘贴或输入你的短视频脚本..."
              />
            </div>
          )}

          {taskMode === "CUSTOM_STORYBOARD" && (
            <div>
              <label className="text-sm font-medium">分镜内容 *</label>
              <textarea
                value={storyboardText}
                onChange={(e) => setStoryboardText(e.target.value)}
                rows={5}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="按镜头列出场景、动作、画面要求..."
              />
            </div>
          )}

          <div>
            <label className="text-sm font-medium">商品名称 *</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="如：Wireless Earbuds Pro"
            />
          </div>

          <div>
            <label className="text-sm font-medium">商品描述</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="描述商品的主要功能、卖点、风格和限制..."
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="text-sm font-medium">目标市场</label>
              <select
                value={targetMarket}
                onChange={(e) => setTargetMarket(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none"
              >
                <option value="US">美国 (US)</option>
                <option value="UK">英国 (UK)</option>
                <option value="JP">日本 (JP)</option>
              </select>
            </div>
            <div>
              <label className="text-sm font-medium">语言</label>
              <select
                value={language}
                onChange={(e) => setLanguage(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none"
              >
                <option value="en">English</option>
                <option value="zh">中文</option>
                <option value="ja">日本語</option>
              </select>
            </div>
          </div>

          <div>
            <label className="text-sm font-medium">商品链接</label>
            <input
              type="url"
              value={productLink}
              onChange={(e) => setProductLink(e.target.value)}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="https://..."
            />
          </div>

          <div>
            <label className="text-sm font-medium">
              商品图片 {taskMode === "PRODUCT_CREATIVE" ? "*" : "（可选）"}
            </label>
            <div className="mt-1 flex gap-2">
              <input
                type="url"
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                className="flex-1 rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="输入图片 URL"
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    addImage();
                  }
                }}
              />
              <button type="button" onClick={addImage} className="rounded-md border px-3 py-2 text-sm hover:bg-accent">
                添加
              </button>
            </div>
            {imageUrls.length > 0 && (
              <div className="mt-2 flex flex-wrap gap-2">
                {imageUrls.map((url, i) => (
                  <div key={url} className="group relative">
                    <img src={url} alt={`Product image ${i + 1}`} className="h-20 w-20 rounded-md border object-cover" />
                    <button
                      type="button"
                      onClick={() => setImageUrls(imageUrls.filter((_, j) => j !== i))}
                      className="absolute -right-1 -top-1 hidden h-5 w-5 items-center justify-center rounded-full bg-destructive text-xs text-white group-hover:flex"
                    >
                      x
                    </button>
                    {i === 0 && <span className="absolute bottom-0 left-0 rounded-tr bg-primary px-1 text-[10px] text-primary-foreground">主图</span>}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div>
            <label className="text-sm font-medium">视频时长</label>
            <div className="mt-2 flex gap-3">
              {V1_DURATIONS.map((d) => (
                <button
                  type="button"
                  key={d}
                  onClick={() => setDuration(d)}
                  className={`rounded-lg border px-4 py-2 text-sm font-medium transition-colors ${
                    duration === d ? "border-primary bg-primary text-primary-foreground" : "hover:bg-accent"
                  }`}
                >
                  {d}s
                </button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-2">
            <input
              type="checkbox"
              id="subtitles"
              checked={needSubtitles}
              onChange={(e) => setNeedSubtitles(e.target.checked)}
              className="h-4 w-4"
            />
            <label htmlFor="subtitles" className="text-sm">生成英文字幕</label>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}

          <div className="flex gap-3">
            <button type="button" onClick={() => setStep("mode")} className="rounded-md border px-4 py-2 text-sm hover:bg-accent">
              上一步
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
            >
              {loading ? "创建中..." : "创建任务"}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
