"use client";

import { apiRequest } from "@/lib/api-client";
import type { Storyboard, StoryboardShot, UpdateStoryboardRequest, VideoTask } from "@/types/api";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Clock, Film, Type, Music, Hash, Save, CheckCircle, ArrowRight } from "lucide-react";
import { CancelTaskButton } from "@/components/cancel-task-button";

export default function StoryboardPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [storyboard, setStoryboard] = useState<Storyboard | null>(null);
  const [taskStatus, setTaskStatus] = useState<string>("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  // Editable fields
  const [title, setTitle] = useState("");
  const [hook, setHook] = useState("");
  const [coverText, setCoverText] = useState("");
  const [caption, setCaption] = useState("");
  const [hashtagsStr, setHashtagsStr] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const [sbRes, taskRes] = await Promise.all([
          apiRequest<{ code: number; message: string; data: Storyboard }>(
            `/api/video-tasks/${id}/storyboard`
          ),
          apiRequest<{ code: number; message: string; data: VideoTask }>(
            `/api/video-tasks/${id}`
          ),
        ]);
        if (sbRes.code === 0 && sbRes.data) {
          setStoryboard(sbRes.data);
          setTitle(sbRes.data.title || "");
          setHook(sbRes.data.hook || "");
          setCoverText(sbRes.data.coverText || "");
          setCaption(sbRes.data.caption || "");
          setHashtagsStr((sbRes.data.hashtags || []).join(", "));
        } else {
          setError(sbRes.message || "加载分镜失败");
        }
        if (taskRes.code === 0 && taskRes.data) {
          setTaskStatus(taskRes.data.status);
        }
      } catch (e: any) {
        setError(e.message || "网络错误");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [id]);

  async function handleSave() {
    if (!storyboard) return;
    setSaving(true);
    setError("");
    setSuccessMsg("");
    try {
      const hashtags = hashtagsStr
        .split(/[,，]/)
        .map((h) => h.trim())
        .filter(Boolean);

      const body: UpdateStoryboardRequest = {
        title,
        hook,
        coverText,
        caption,
        hashtags,
      };

      const res = await apiRequest<{ code: number; message: string }>(
        `/api/storyboards/${storyboard.storyboardId}`,
        { method: "PATCH", body }
      );
      if (res.code === 0) {
        setSuccessMsg("保存成功");
        setTimeout(() => setSuccessMsg(""), 3000);
      } else {
        setError(res.message || "保存失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setSaving(false);
    }
  }

  async function handleConfirm() {
    setConfirming(true);
    setError("");
    try {
      const res = await apiRequest<{ code: number; message: string }>(
        `/api/video-tasks/${id}/confirm-storyboard`,
        { method: "POST" }
      );
      if (res.code === 0) {
        router.push(`/video-tasks/${id}/keyframes`);
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

  if (!storyboard) {
    return (
      <div className="p-8">
        <p className="text-destructive">分镜数据不存在 — 可能 AI 尚未生成</p>
        <Link href={`/video-tasks/${id}/progress`} className="mt-2 inline-block text-sm text-primary hover:underline">
          ← 返回进度页
        </Link>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <Link href={`/video-tasks/${id}/progress`} className="text-sm text-muted-foreground hover:text-foreground">
            ← 返回进度
          </Link>
          <h1 className="mt-2 text-2xl font-bold">分镜脚本编辑</h1>
        </div>
        <div className="flex items-center gap-3">
          <CancelTaskButton taskId={id} />
          <button
            onClick={handleSave}
            disabled={saving}
            className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            <Save className="h-4 w-4" />
            {saving ? "保存中..." : "保存修改"}
          </button>
          {taskStatus === "waiting_storyboard_confirmation" && (
            <button
              onClick={handleConfirm}
              disabled={confirming}
              className="inline-flex items-center gap-2 rounded-md bg-green-600 px-4 py-2 text-sm font-medium text-white hover:bg-green-700 disabled:opacity-50"
            >
              <CheckCircle className="h-4 w-4" />
              {confirming ? "确认中..." : "确认分镜，进入关键帧"}
            </button>
          )}
        </div>
      </div>

      {taskStatus === "waiting_storyboard_confirmation" && (
        <div className="flex items-center gap-2 rounded-md border border-green-500/50 bg-green-50 p-4 text-sm text-green-700">
          <CheckCircle className="h-5 w-5 shrink-0" />
          <span>AI 分镜已生成。请检查并编辑分镜内容，确认无误后点击「确认分镜」进入关键帧配置。</span>
        </div>
      )}

      {successMsg && (
        <div className="rounded-md bg-green-50 p-3 text-sm text-green-700">{successMsg}</div>
      )}
      {error && <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>}

      {/* Editable text fields */}
      <div className="grid gap-4 rounded-lg border bg-card p-6">
        <div>
          <label className="text-sm font-medium">标题</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </div>
        <div>
          <label className="text-sm font-medium">Hook / 开场</label>
          <textarea
            value={hook}
            onChange={(e) => setHook(e.target.value)}
            rows={2}
            className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="text-sm font-medium">封面文案</label>
            <input
              type="text"
              value={coverText}
              onChange={(e) => setCoverText(e.target.value)}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
          <div>
            <label className="text-sm font-medium">字幕/文案</label>
            <input
              type="text"
              value={caption}
              onChange={(e) => setCaption(e.target.value)}
              className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </div>
        </div>
        <div>
          <label className="text-sm font-medium">
            <Hash className="mr-1 inline h-3 w-3" />
            标签 (逗号分隔)
          </label>
          <input
            type="text"
            value={hashtagsStr}
            onChange={(e) => setHashtagsStr(e.target.value)}
            className="mt-1 w-full rounded-md border px-3 py-2 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            placeholder="如: #wireless, #earbuds, #tech"
          />
        </div>
      </div>

      {/* Read-only info */}
      <div className="grid gap-4 rounded-lg border bg-card p-6">
        <div className="flex items-center gap-2 text-sm">
          <Type className="h-4 w-4 text-muted-foreground" />
          <span className="font-medium">完整脚本:</span>
          <span className="text-muted-foreground">{storyboard.script || "—"}</span>
        </div>
        {storyboard.musicSuggestion && (
          <div className="flex items-center gap-2 text-sm">
            <Music className="h-4 w-4 text-muted-foreground" />
            <span className="font-medium">音乐建议:</span>
            <span className="text-muted-foreground">{storyboard.musicSuggestion}</span>
          </div>
        )}
      </div>

      {/* Shot list */}
      <div className="space-y-4">
        <h2 className="text-lg font-semibold flex items-center gap-2">
          <Film className="h-5 w-5" />
          镜头列表 ({storyboard.shots?.length || 0} 个)
        </h2>
        {storyboard.shots && storyboard.shots.length > 0 ? (
          <div className="space-y-3">
            {storyboard.shots.map((shot: StoryboardShot, i: number) => (
              <div key={i} className="rounded-lg border bg-card p-4">
                <div className="mb-3 flex items-center justify-between">
                  <span className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-medium text-primary">
                    镜头 {shot.shotNo ?? i + 1}
                  </span>
                  <span className="inline-flex items-center gap-1 text-xs text-muted-foreground">
                    <Clock className="h-3 w-3" />
                    {shot.duration ?? 0}s
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
                  <div className="col-span-2">
                    <dt className="text-xs text-muted-foreground">字幕</dt>
                    <dd>{shot.subtitle || "—"}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">素材类型</dt>
                    <dd className="inline-flex items-center rounded bg-accent px-2 py-0.5 text-xs font-mono">
                      {shot.materialType || "—"}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-muted-foreground">编辑指令</dt>
                    <dd>{shot.editInstruction || "—"}</dd>
                  </div>
                  {shot.prompt && (
                    <div className="col-span-2">
                      <dt className="text-xs text-muted-foreground">AI Prompt</dt>
                      <dd className="mt-0.5 rounded bg-muted p-2 text-xs font-mono text-muted-foreground">
                        {shot.prompt}
                      </dd>
                    </div>
                  )}
                </dl>
              </div>
            ))}
          </div>
        ) : (
          <div className="rounded-lg border bg-card p-8 text-center">
            <p className="text-sm text-muted-foreground">暂无镜头数据</p>
          </div>
        )}
      </div>
    </div>
  );
}
