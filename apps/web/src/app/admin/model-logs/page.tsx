"use client";

import { apiRequest } from "@/lib/api-client";
import type { AdminLogItem } from "@/types/api";
import { useEffect, useState } from "react";
import { Cpu } from "lucide-react";

function formatCost(cost: unknown): string {
  if (typeof cost === "number") return `$${cost.toFixed(4)}`;
  return "—";
}

export default function AdminModelLogsPage() {
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
      }>(`/api/admin/model-logs?page=${pageNum}&pageSize=20`);

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
        <Cpu className="h-6 w-6 text-primary" />
        <div>
          <h1 className="text-2xl font-bold">AI 模型日志</h1>
          <p className="text-sm text-muted-foreground">AI 调用成本与状态追踪 · 共 {total} 条</p>
        </div>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : logs.length === 0 ? (
        <div className="rounded-lg border bg-card p-12 text-center">
          <p className="text-muted-foreground">暂无 AI 调用记录</p>
        </div>
      ) : (
        <>
          <div className="overflow-hidden rounded-lg border">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium">Service</th>
                    <th className="px-4 py-3 text-left font-medium">Provider</th>
                    <th className="px-4 py-3 text-left font-medium">Model</th>
                    <th className="px-4 py-3 text-left font-medium">Task Type</th>
                    <th className="px-4 py-3 text-right font-medium">Input Tokens</th>
                    <th className="px-4 py-3 text-right font-medium">Output Tokens</th>
                    <th className="px-4 py-3 text-right font-medium">Cost</th>
                    <th className="px-4 py-3 text-left font-medium">Status</th>
                    <th className="px-4 py-3 text-left font-medium">Error</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {logs.map((log, i) => (
                    <tr key={i} className="hover:bg-accent/50">
                      <td className="px-4 py-3">{String(log.service || "—")}</td>
                      <td className="px-4 py-3">{String(log.provider || "—")}</td>
                      <td className="px-4 py-3 font-mono text-xs">{String(log.modelName || "—")}</td>
                      <td className="px-4 py-3">
                        <span className="inline-flex items-center rounded bg-accent px-2 py-0.5 text-xs">
                          {String(log.taskType || "—")}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">{String(log.inputTokens ?? "—")}</td>
                      <td className="px-4 py-3 text-right">{String(log.outputTokens ?? "—")}</td>
                      <td className="px-4 py-3 text-right font-mono text-xs">
                        {formatCost(log.cost)}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                            log.status === "success"
                              ? "bg-green-100 text-green-700"
                              : log.status === "failed"
                              ? "bg-red-100 text-red-700"
                              : "bg-yellow-100 text-yellow-700"
                          }`}
                        >
                          {String(log.status || "—")}
                        </span>
                      </td>
                      <td className="px-4 py-3 max-w-[200px]">
                        {log.errorMessage ? (
                          <span className="text-xs text-destructive truncate block">
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
