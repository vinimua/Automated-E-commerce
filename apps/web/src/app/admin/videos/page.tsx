"use client";

import { apiRequest } from "@/lib/api-client";
import type { AdminVideoListData, Video } from "@/types/api";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

const VIDEO_STATUS_LABELS: Record<string, string> = {
  completed: "Completed",
  exported: "Exported",
  deleted: "Deleted",
};

export default function AdminVideosPage() {
  const [videos, setVideos] = useState<Video[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const load = useCallback(async (pageNum: number) => {
    setLoading(true);
    setError("");
    try {
      const params = new URLSearchParams({
        page: String(pageNum),
        pageSize: "20",
      });
      if (status) params.set("status", status);

      const res = await apiRequest<{
        code: number;
        message: string;
        data: AdminVideoListData;
      }>(`/api/admin/videos?${params.toString()}`);

      if (res.code === 0 && res.data) {
        setVideos(res.data.items || []);
        setTotal(res.data.total || 0);
        setTotalPages(res.data.totalPages || 1);
      } else {
        setError(res.message || "Failed to load videos");
      }
    } catch (e: any) {
      setError(e.message || "Failed to load videos");
    } finally {
      setLoading(false);
    }
  }, [status]);

  useEffect(() => {
    load(page);
  }, [load, page]);

  return (
    <div className="space-y-6 p-8">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Video Management</h1>
          <p className="text-sm text-muted-foreground">All generated videos · {total} total</p>
        </div>
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(1);
          }}
          className="rounded-md border px-3 py-1.5 text-sm focus:border-primary focus:outline-none"
        >
          <option value="">All statuses</option>
          <option value="completed">Completed</option>
          <option value="exported">Exported</option>
          <option value="deleted">Deleted</option>
        </select>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-6 text-center">
          <p className="text-sm text-destructive">{error}</p>
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium">Video ID</th>
                    <th className="px-4 py-3 text-left font-medium">Task ID</th>
                    <th className="px-4 py-3 text-left font-medium">Title</th>
                    <th className="px-4 py-3 text-left font-medium">Duration</th>
                    <th className="px-4 py-3 text-left font-medium">Status</th>
                    <th className="px-4 py-3 text-left font-medium">Quality</th>
                    <th className="px-4 py-3 text-left font-medium">Risk</th>
                    <th className="px-4 py-3 text-left font-medium">Preview</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {videos.map((video) => (
                    <tr key={video.videoId} className="hover:bg-accent/50">
                      <td className="px-4 py-3 font-mono text-xs" title={video.videoId}>
                        {video.videoId?.slice(0, 8)}...
                      </td>
                      <td className="px-4 py-3 font-mono text-xs" title={video.taskId}>
                        {video.taskId?.slice(0, 8)}...
                      </td>
                      <td className="max-w-[220px] truncate px-4 py-3" title={video.title || undefined}>
                        {video.title || "-"}
                      </td>
                      <td className="px-4 py-3">{video.duration ? `${video.duration}s` : "-"}</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                            video.status === "exported"
                              ? "bg-green-100 text-green-700"
                              : video.status === "deleted"
                              ? "bg-red-100 text-red-700"
                              : "bg-blue-100 text-blue-700"
                          }`}
                        >
                          {VIDEO_STATUS_LABELS[video.status || ""] || video.status || "-"}
                        </span>
                      </td>
                      <td className="px-4 py-3">{video.qualityScore ?? "-"}</td>
                      <td className="px-4 py-3">{video.riskScore ?? "-"}</td>
                      <td className="px-4 py-3">
                        {video.videoId ? (
                          <Link
                            href={`/videos/${video.videoId}`}
                            className="text-sm font-medium text-primary hover:underline"
                          >
                            Open
                          </Link>
                        ) : (
                          "-"
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2">
              <button
                onClick={() => setPage(Math.max(1, page - 1))}
                disabled={page <= 1}
                className="rounded-md border px-3 py-1.5 text-sm hover:bg-accent disabled:opacity-50"
              >
                Previous
              </button>
              <span className="text-sm text-muted-foreground">{page} / {totalPages}</span>
              <button
                onClick={() => setPage(Math.min(totalPages, page + 1))}
                disabled={page >= totalPages}
                className="rounded-md border px-3 py-1.5 text-sm hover:bg-accent disabled:opacity-50"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
