"use client";

import { apiRequest } from "@/lib/api-client";
import { STATUS_LABELS, VIDEO_TYPE_LABELS } from "@/types/api";
import type { VideoTask } from "@/types/api";
import { useEffect, useState } from "react";

export default function AdminVideoTasksPage() {
  const [tasks, setTasks] = useState<VideoTask[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  async function load(pageNum: number) {
    setLoading(true);
    try {
      const res = await apiRequest<{
        code: number; message: string;
        data: { items: VideoTask[]; page: number; pageSize: number; total: number; totalPages: number };
      }>(`/api/admin/video-tasks?page=${pageNum}&pageSize=20`);

      if (res.code === 0 && res.data) {
        setTasks(res.data.items || []);
        setTotal(res.data.total);
        setTotalPages(res.data.totalPages);
      }
    } catch {
      // silently ignore
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(page); }, [page]);

  return (
    <div className="space-y-6 p-8">
      <div>
        <h1 className="text-2xl font-bold">任务管理</h1>
        <p className="text-sm text-muted-foreground">所有用户的任务列表 · 共 {total} 条</p>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium">Task ID</th>
                    <th className="px-4 py-3 text-left font-medium">Product</th>
                    <th className="px-4 py-3 text-left font-medium">Type</th>
                    <th className="px-4 py-3 text-left font-medium">Duration</th>
                    <th className="px-4 py-3 text-left font-medium">Status</th>
                    <th className="px-4 py-3 text-left font-medium">Progress</th>
                    <th className="px-4 py-3 text-left font-medium">Error</th>
                    <th className="px-4 py-3 text-left font-medium">Retries</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {tasks.map((task) => (
                    <tr key={task.taskId} className="hover:bg-accent/50">
                      <td className="px-4 py-3 font-mono text-xs" title={task.taskId}>
                        {task.taskId?.slice(0, 8)}...
                      </td>
                      <td className="px-4 py-3 font-mono text-xs" title={task.productId}>
                        {task.productId?.slice(0, 8)}...
                      </td>
                      <td className="px-4 py-3">
                        {VIDEO_TYPE_LABELS[task.videoType] || task.videoType}
                      </td>
                      <td className="px-4 py-3">{task.duration}s</td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                            task.status === "completed" || task.status === "exported"
                              ? "bg-green-100 text-green-700"
                              : task.status === "failed"
                              ? "bg-red-100 text-red-700"
                              : "bg-blue-100 text-blue-700"
                          }`}
                        >
                          {STATUS_LABELS[task.status] || task.status}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="h-1.5 w-16 rounded-full bg-muted">
                            <div
                              className="h-full rounded-full bg-primary transition-all"
                              style={{ width: `${task.progress || 0}%` }}
                            />
                          </div>
                          <span className="text-xs">{task.progress || 0}%</span>
                        </div>
                      </td>
                      <td className="px-4 py-3 max-w-[200px]">
                        {task.errorMessage ? (
                          <span className="text-xs text-destructive truncate block" title={task.errorMessage || undefined}>
                            {task.errorMessage}
                          </span>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3">{task.retryCount || 0}</td>
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
                上一页
              </button>
              <span className="text-sm text-muted-foreground">{page} / {totalPages}</span>
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
