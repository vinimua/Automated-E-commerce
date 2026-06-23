"use client";

import { apiRequest } from "@/lib/api-client";
import type { Video } from "@/types/api";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Download, Film, Hash, AlertTriangle, CheckCircle } from "lucide-react";

export default function VideoPreviewPage() {
  const { id } = useParams<{ id: string }>();
  const [video, setVideo] = useState<Video | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const res = await apiRequest<{ code: number; message: string; data: Video }>(
          `/api/videos/${id}`
        );
        if (res.code === 0) setVideo(res.data);
        else setError(res.message || "加载失败");
      } catch (e: any) {
        setError(e.message || "网络错误");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [id]);

  async function handleExport() {
    if (!video) return;
    setExporting(true);
    setError("");
    try {
      const res = await apiRequest<{
        code: number; message: string;
        data: { videoId: string; status: string; downloadUrl: string };
      }>(`/api/videos/${video.videoId}/export`, { method: "POST" });
      if (res.code === 0 && res.data) {
        setVideo({ ...video, status: "exported" as any });
        // Trigger download
        if (res.data.downloadUrl) {
          window.open(res.data.downloadUrl, "_blank");
        }
      } else {
        setError(res.message || "导出失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setExporting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!video) {
    return (
      <div className="p-8">
        <Link href="/videos" className="text-sm text-muted-foreground hover:text-foreground">
          ← 返回视频库
        </Link>
        <p className="mt-4 text-destructive">视频不存在</p>
      </div>
    );
  }

  const isExported = video.status === "exported";
  const hasVideo = !!video.videoUrl;

  return (
    <div className="mx-auto max-w-4xl space-y-8 p-8">
      <Link href="/videos" className="text-sm text-muted-foreground hover:text-foreground">
        ← 返回视频库
      </Link>

      {/* Video player */}
      <div className="overflow-hidden rounded-lg border bg-black">
        {hasVideo ? (
          <video
            controls
            poster={video.coverUrl}
            className="mx-auto max-h-[70vh] w-full"
            style={{ aspectRatio: "9/16" }}
          >
            <source src={video.videoUrl} type="video/mp4" />
            你的浏览器不支持视频播放
          </video>
        ) : (
          <div className="flex aspect-[9/16] items-center justify-center">
            <div className="text-center text-white">
              <Film className="mx-auto h-16 w-16 opacity-50" />
              <p className="mt-4 opacity-70">视频尚未生成</p>
            </div>
          </div>
        )}
      </div>

      {/* Video info */}
      <div className="grid gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2 space-y-4">
          <div>
            <h1 className="text-2xl font-bold">{video.title || "未命名视频"}</h1>
            {video.caption && <p className="mt-2 text-muted-foreground">{video.caption}</p>}
          </div>

          {/* Hashtags */}
          {video.hashtags && video.hashtags.length > 0 && (
            <div className="flex flex-wrap gap-1.5">
              {video.hashtags.map((tag: string) => (
                <span key={tag} className="inline-flex items-center gap-1 rounded-full bg-accent px-2.5 py-0.5 text-xs">
                  <Hash className="h-3 w-3" />
                  {tag}
                </span>
              ))}
            </div>
          )}

          {/* Download button */}
          <div className="flex gap-3">
            {isExported && video.videoUrl ? (
              <a
                href={video.videoUrl}
                download
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              >
                <Download className="h-4 w-4" />
                下载视频
              </a>
            ) : (
              <button
                onClick={handleExport}
                disabled={exporting || !hasVideo}
                className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
              >
                <Download className="h-4 w-4" />
                {exporting ? "导出中..." : "导出并下载"}
              </button>
            )}
          </div>
        </div>

        {/* Meta sidebar */}
        <div className="space-y-4 rounded-lg border bg-card p-4">
          <h3 className="text-sm font-semibold">视频信息</h3>
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-muted-foreground">时长</dt>
              <dd>{video.duration}s</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-muted-foreground">分辨率</dt>
              <dd>{video.resolution || "1080×1920"}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-muted-foreground">状态</dt>
              <dd className={isExported ? "text-green-600 font-medium" : ""}>
                {isExported ? "已导出" : "已完成"}
              </dd>
            </div>
            {video.qualityScore != null && (
              <div className="flex justify-between">
                <dt className="text-muted-foreground flex items-center gap-1">
                  <CheckCircle className="h-3 w-3" />
                  质量评分
                </dt>
                <dd>{video.qualityScore}/100</dd>
              </div>
            )}
            {video.riskScore != null && (
              <div className="flex justify-between">
                <dt className={`flex items-center gap-1 ${video.riskScore > 50 ? "text-yellow-600" : "text-muted-foreground"}`}>
                  <AlertTriangle className="h-3 w-3" />
                  风险评分
                </dt>
                <dd className={video.riskScore > 50 ? "text-yellow-600 font-medium" : ""}>
                  {video.riskScore}/100
                </dd>
              </div>
            )}
          </dl>
        </div>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  );
}
