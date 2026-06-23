"use client";

import { apiRequest } from "@/lib/api-client";
import type { AdminLogItem } from "@/types/api";
import { useEffect, useState } from "react";
import { Monitor } from "lucide-react";

export default function AdminRenderLogsPage() {
  const [logs, setLogs] = useState<AdminLogItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);

  async function load(pageNum: number) {
    setLoading(true);
    try {
      const res = await apiRequest<{
        code: number; message: string;
        data: { items: AdminLogItem[]; total: number };
      }>(`/api/admin/render-logs?page=${pageNum}&pageSize=20`);

      if (res.code === 0 && res.data) {
        setLogs(res.data.items || []);
        setTotal(res.data.total);
      }
    } catch {
      // silently ignore
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { load(page); }, [page]);

  const totalPages = Math.ceil(total / 20);

  return (
    <div className="space-y-6 p-8">
      <div className="flex items-center gap-2">
        <Monitor className="h-6 w-6 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">渲染日志</h1>
          <p className="text-sm text-muted-foreground">视频渲染成功/失败记录 · 共 {total} 条</p>
        </div>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : logs.length === 0 ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <p className="text-muted-foreground">暂无渲染记录</p>
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium">Task ID</th>
                    <th className="px-4 py-3 text-left font-medium">Video ID</th>
                    <th className="px-4 py-3 text-left font-medium">Render Task</th>
                    <th className="px-4 py-3 text-left font-medium">Template</th>
                    <th className="px-4 py-3 text-left font-medium">Status</th>
                    <th className="px-4 py-3 text-right font-medium">Duration</th>
                    <th className="px-4 py-3 text-left font-medium">Output URL</th>
                    <th className="px-4 py-3 text-left font-medium">Error</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {logs.map((log, i) => (
                    <tr key={i} className="hover:bg-accent/50">
                      <td className="px-4 py-3 font-mono text-xs" title={String(log.taskId || "")}>
                        {String(log.taskId || "—").slice(0, 8)}...
                      </td>
                      <td className="px-4 py-3 font-mono text-xs">
                        {log.videoId ? String(log.videoId).slice(0, 8) + "..." : "—"}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs">
                        {log.renderTaskId ? String(log.renderTaskId).slice(0, 8) + "..." : "—"}
                      </td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center rounded bg-accent px-2 py-0.5 text-xs">
                          {String(log.template || "—")}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                            log.status === "success" || log.status === "completed"
                              ? "bg-green-100 text-green-700"
                              : log.status === "failed"
                              ? "bg-red-100 text-red-700"
                              : "bg-yellow-100 text-yellow-700"
                          }`}
                        >
                          {String(log.status || "—")}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        {log.durationSeconds != null ? `${log.durationSeconds}s` : "—"}
                      </td>
                      <td className="px-4 py-3 max-w-[200px]">
                        {log.outputUrl ? (
                          <a
                            href={String(log.outputUrl)}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-xs text-primary hover:underline truncate block"
                          >
                            {String(log.outputUrl).slice(0, 40)}...
                          </a>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
                        )}
                      </td>
                      <td className="px-4 py-3 max-w-[200px]">
                        {log.errorMessage ? (
                          <span className="text-xs text-destructive truncate block" title={String(log.errorMessage)}>
                            {String(log.errorMessage)}
                          </span>
                        ) : (
                          <span className="text-xs text-muted-foreground">—</span>
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
