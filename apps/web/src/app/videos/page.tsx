"use client";

import { apiRequest } from "@/lib/api-client";
import type { Video, VideoStatus } from "@/types/api";
import { STATUS_LABELS } from "@/types/api";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Film, Filter } from "lucide-react";

const VIDEO_STATUS_LABELS: Record<string, string> = {
  completed: "已完成",
  exported: "已导出",
  deleted: "已删除",
};

export default function VideosPage() {
  const [videos, setVideos] = useState<Video[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const load = useCallback(async (pageNum: number = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set("page", String(pageNum));
      params.set("pageSize", "12");
      if (filterStatus) params.set("status", filterStatus);

      const res = await apiRequest<{
        code: number; message: string;
        data: { items: Video[]; page: number; pageSize: number; total: number; totalPages: number };
      }>(`/api/videos?${params.toString()}`);

      if (res.code === 0 && res.data) {
        setVideos(res.data.items || []);
        setTotal(res.data.total);
        setTotalPages(res.data.totalPages);
      } else {
        setError(res.message || "加载失败");
      }
    } catch (e: any) {
      setError(e.message || "网络错误");
    } finally {
      setLoading(false);
    }
  }, [filterStatus]);

  useEffect(() => {
    load(page);
  }, [load, page]);

  return (
    <div className="space-y-8 p-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">视频库</h1>
          <p className="text-sm text-muted-foreground">共 {total} 个视频</p>
        </div>

        {/* Filter */}
        <div className="flex items-center gap-2">
          <Filter className="h-4 w-4 text-muted-foreground" />
          <select
            value={filterStatus}
            onChange={(e) => { setFilterStatus(e.target.value); setPage(1); }}
            className="rounded-md border px-3 py-1.5 text-sm focus:border-primary focus:outline-none"
          >
            <option value="">全部状态</option>
            <option value="completed">已完成</option>
            <option value="exported">已导出</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-6 text-center">
          <p className="text-sm text-destructive">{error}</p>
        </div>
      ) : videos.length === 0 ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <Film className="mx-auto h-12 w-12 text-muted-foreground" />
          <p className="mt-4 text-lg font-medium">还没有视频</p>
          <p className="mt-1 text-sm text-muted-foreground">完成一个视频任务后，视频会出现在这里</p>
          <Link href="/products/new" className="mt-4 inline-block text-sm font-medium text-primary hover:underline">
            创建视频任务 →
          </Link>
        </div>
      ) : (
        <>
          {/* Video grid */}
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {videos.map((video) => (
              <Link
                key={video.videoId}
                href={`/videos/${video.videoId}`}
                className="group rounded-lg border bg-card transition-colors hover:bg-accent/50 overflow-hidden"
              >
                {/* Thumbnail */}
                <div className="aspect-[9/16] bg-muted relative overflow-hidden">
                  {video.coverUrl ? (
                    <img
                      src={video.coverUrl}
                      alt={video.title || "Video cover"}
                      className="h-full w-full object-cover"
                    />
                  ) : (
                    <div className="flex h-full items-center justify-center">
                      <Film className="h-12 w-12 text-muted-foreground" />
                    </div>
                  )}
                  <div className="absolute bottom-2 right-2 rounded bg-black/70 px-1.5 py-0.5 text-xs text-white">
                    {video.duration}s
                  </div>
                </div>
                {/* Info */}
                <div className="p-3 space-y-1">
                  <p className="font-medium text-sm truncate">{video.title || "未命名视频"}</p>
                  {video.caption && (
                    <p className="text-xs text-muted-foreground truncate">{video.caption}</p>
                  )}
                  <div className="flex items-center gap-2 pt-1">
                    <span className="text-xs text-muted-foreground">{video.resolution}</span>
                    <span
                      className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium ${
                        video.status === "exported"
                          ? "bg-green-100 text-green-700"
                          : "bg-blue-100 text-blue-700"
                      }`}
                    >
                      {VIDEO_STATUS_LABELS[video.status || ""] || video.status}
                    </span>
                    {video.qualityScore != null && (
                      <span className="text-[10px] text-muted-foreground">
                        质量: {video.qualityScore}
                      </span>
                    )}
                  </div>
                </div>
              </Link>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2">
              <button
                onClick={() => setPage(Math.max(1, page - 1))}
                disabled={page <= 1}
                className="rounded-md border px-3 py-1.5 text-sm hover:bg-accent disabled:opacity-50"
              >
                上一页
              </button>
              <span className="text-sm text-muted-foreground">
                {page} / {totalPages}
              </span>
              <button
                onClick={() => setPage(Math.min(totalPages, page + 1))}
                disabled={page >= totalPages}
                className="rounded-md border px-3 py-1.5 text-sm hover:bg-accent disabled:opacity-50"
              >
                下一页
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
