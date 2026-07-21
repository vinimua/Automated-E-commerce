"use client";

import { apiRequest, uploadFile } from "@/lib/api-client";
import { V1_DURATIONS } from "@/types/api";
import type { CreateFashionTaskRequest, FashionTaskCreateResponse, TaskMode } from "@/types/api";
import { useRouter } from "next/navigation";
import { useState } from "react";

type Step = "mode" | "details";

const TASK_MODES: Array<{ value: TaskMode; title: string; desc: string }> = [
  {
    value: "PRODUCT_CREATIVE",
    title: "AI 根据商品生成创意",
    desc: "从商品图和卖点出发，生成差异化创意方案。",
  },
  {
    value: "REFERENCE_STORYBOARD",
    title: "按参考视频分镜生成",
    desc: "先上传参考视频，AI 拆解分镜，再替换成你的商品内容。",
  },
  {
    value: "USER_SCRIPT",
    title: "我有脚本",
    desc: "用户提供脚本，AI 拆解分镜、关键帧和视频片段。",
  },
  {
    value: "CUSTOM_STORYBOARD",
    title: "我有分镜",
    desc: "用户提供分镜结构，AI 补齐画面与片段生成。",
  },
];

export default function NewProductPage() {
  const router = useRouter();
  const [step, setStep] = useState<Step>("mode");

  const [taskMode, setTaskMode] = useState<TaskMode>("PRODUCT_CREATIVE");
  const [duration, setDuration] = useState<CreateFashionTaskRequest["duration"]>(20);
  const [needSubtitles, setNeedSubtitles] = useState(true);

  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [targetMarket, setTargetMarket] = useState("US");
  const [language, setLanguage] = useState("zh");

  const [referenceVideoUrl, setReferenceVideoUrl] = useState("");
  const [scriptText, setScriptText] = useState("");
  const [storyboardText, setStoryboardText] = useState("");
  const [imageUrls, setImageUrls] = useState<string[]>([]);

  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [uploadingFile, setUploadingFile] = useState<string | null>(null);

  async function doUpload(file: File, folder: string): Promise<string | null> {
    setUploadingFile(file.name);
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("folder", folder);
      const res = await uploadFile<{ code: number; message: string; data: { fileUrl: string } }>(
        "/api/storage/upload", formData
      );
      if (res.code === 0 && res.data?.fileUrl) {
        return res.data.fileUrl;
      }
      setError("上传失败: " + (res.message || "未知错误"));
      return null;
    } catch (e: any) {
      setError("上传失败: " + (e.message || "网络错误"));
      return null;
    } finally {
      setUploadingFile(null);
    }
  }

  async function handleImageUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const files = e.target.files;
    if (!files) return;
    for (const file of Array.from(files)) {
      const url = await doUpload(file, "product-images");
      if (url) setImageUrls(prev => [...prev, url]);
    }
  }

  async function handleVideoUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    const url = await doUpload(file, "reference-videos");
    if (url) setReferenceVideoUrl(url);
  }

  function validateDetails() {
    if (!name.trim()) {
      return "请输入商品名称";
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
      const body: CreateFashionTaskRequest = {
        name: name.trim(),
        description,
        imageUrls: imageUrls.length > 0 ? imageUrls : undefined,
        targetMarket: targetMarket as CreateFashionTaskRequest["targetMarket"],
        language: language as CreateFashionTaskRequest["language"],
        duration,
        needSubtitles,
        taskMode,
        referenceVideoUrl: taskMode === "REFERENCE_STORYBOARD" ? referenceVideoUrl.trim() : undefined,
        scriptText: taskMode === "USER_SCRIPT" ? scriptText.trim() : undefined,
        storyboardText: taskMode === "CUSTOM_STORYBOARD" ? storyboardText.trim() : undefined,
      };

      const res = await apiRequest<FashionTaskCreateResponse>("/api/fashion-video-tasks", {
        method: "POST",
        body,
      });

      if (res.code !== 0 || !res.data?.taskId) {
        setError(res.message || "创建任务失败");
        return;
      }

      const status = res.data.status;
      if (status === "asset_uploading" || status === "asset_analyzing" || status === "waiting_asset_confirmation") {
        router.push(`/video-tasks/${res.data.taskId}/assets`);
      } else {
        router.push(`/video-tasks/${res.data.taskId}/plans`);
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
          {/* File uploads: images + video */}
          <div className="space-y-3 rounded-lg border bg-muted/30 p-4">
            <p className="text-sm font-medium">上传素材</p>

            <div>
              <label className="text-xs text-muted-foreground">商品图片 (可多选)</label>
              <input
                type="file"
                accept="image/*"
                multiple
                onChange={handleImageUpload}
                disabled={!!uploadingFile}
                className="mt-1 w-full text-sm file:mr-3 file:rounded file:border-0 file:bg-primary file:px-3 file:py-1.5 file:text-xs file:text-primary-foreground hover:file:bg-primary/90"
              />
              {imageUrls.length > 0 && (
                <p className="mt-1 text-xs text-muted-foreground">已上传 {imageUrls.length} 张图片</p>
              )}
              {uploadingFile && <p className="mt-1 text-xs text-primary">上传中: {uploadingFile}...</p>}
            </div>

            {taskMode === "REFERENCE_STORYBOARD" && (
              <div>
                <label className="text-xs text-muted-foreground">
                  参考视频 {referenceVideoUrl ? "✓" : ""}
                </label>
                <input
                  type="file"
                  accept="video/*"
                  onChange={handleVideoUpload}
                  disabled={!!uploadingFile}
                  className="mt-1 w-full text-sm file:mr-3 file:rounded file:border-0 file:bg-primary file:px-3 file:py-1.5 file:text-xs file:text-primary-foreground hover:file:bg-primary/90"
                />
                {referenceVideoUrl && (
                  <p className="mt-1 text-xs text-muted-foreground truncate">视频: {referenceVideoUrl}</p>
                )}
              </div>
            )}

            <p className="text-xs text-muted-foreground">
              或手动输入 URL：
            </p>
          </div>

          {/* Manual URL inputs */}
          <div>
            <label className="text-sm font-medium">商品图片 URL (每行一个)</label>
            <textarea
              value={imageUrls.join("\n")}
              onChange={(e) => setImageUrls(e.target.value.split("\n").filter(Boolean))}
              rows={2}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
              placeholder="https://..."
            />
          </div>

          {taskMode === "REFERENCE_STORYBOARD" && (
            <div>
              <label className="text-sm font-medium">参考视频 URL</label>
              <input
                type="url"
                value={referenceVideoUrl}
                onChange={(e) => setReferenceVideoUrl(e.target.value)}
                className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="https://...（或直接上传视频文件）"
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
                <option value="zh">中文</option>
                <option value="en">English</option>
                <option value="ja">日本語</option>
              </select>
            </div>
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
