"use client";

import { apiRequest } from "@/lib/api-client";
import { STATUS_LABELS } from "@/types/api";
import type { VideoTask } from "@/types/api";
import { useEffect, useState } from "react";
import { BarChart3, CheckCircle, Clock, AlertTriangle, XCircle } from "lucide-react";

interface Stats {
  total: number;
  active: number;
  completed: number;
  failed: number;
}

export default function AdminDashboardPage() {
  const [stats, setStats] = useState<Stats>({ total: 0, active: 0, completed: 0, failed: 0 });
  const [recentTasks, setRecentTasks] = useState<VideoTask[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        // Fetch all tasks to compute stats (admin endpoint)
        const res = await apiRequest<{
          code: number; message: string;
          data: { items: VideoTask[]; total: number };
        }>("/api/admin/video-tasks?pageSize=100");

        if (res.code === 0 && res.data) {
          const items = res.data.items || [];
          const active = items.filter(
            (t) => !["completed", "exported", "failed", "draft"].includes(t.status)
          );
          const completed = items.filter((t) => t.status === "completed" || t.status === "exported");
          const failed = items.filter((t) => t.status === "failed");

          setStats({
            total: res.data.total,
            active: active.length,
            completed: completed.length,
            failed: failed.length,
          });
          setRecentTasks(items.slice(0, 10));
        }
      } catch {
        // Silently ignore
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  if (loading) {
    return (
      <div className="flex h-96 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  const statCards = [
    { label: "总任务", value: stats.total, icon: BarChart3, color: "text-blue-600 bg-blue-50" },
    { label: "进行中", value: stats.active, icon: Clock, color: "text-yellow-600 bg-yellow-50" },
    { label: "已完成", value: stats.completed, icon: CheckCircle, color: "text-green-600 bg-green-50" },
    { label: "失败", value: stats.failed, icon: XCircle, color: "text-red-600 bg-red-50" },
  ];

  return (
    <div className="space-y-8 p-8">
      <div>
        <h1 className="text-2xl font-bold">管理仪表盘</h1>
        <p className="text-sm text-muted-foreground">系统运行概览</p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {statCards.map((s) => {
          const Icon = s.icon;
          return (
            <div key={s.label} className="rounded-lg border bg-card p-6">
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">{s.label}</span>
                <span className={`rounded-lg p-2 ${s.color}`}>
                  <Icon className="h-4 w-4" />
                </span>
              </div>
              <p className="mt-2 text-3xl font-bold">{s.value}</p>
            </div>
          );
        })}
      </div>

      {/* Recent tasks */}
      <div>
        <h2 className="mb-4 text-lg font-semibold">最近任务</h2>
        {recentTasks.length === 0 ? (
          <div className="rounded-lg border bg-card p-8 text-center">
            <p className="text-muted-foreground">暂无数据</p>
          </div>
        ) : (
          <div className="overflow-hidden rounded-lg border">
            <table className="w-full text-sm">
              <thead className="bg-muted/50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium">Task ID</th>
                  <th className="px-4 py-3 text-left font-medium">Product ID</th>
                  <th className="px-4 py-3 text-left font-medium">Type</th>
                  <th className="px-4 py-3 text-left font-medium">Status</th>
                  <th className="px-4 py-3 text-left font-medium">Duration</th>
                  <th className="px-4 py-3 text-left font-medium">Error</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {recentTasks.map((task) => (
                  <tr key={task.taskId} className="hover:bg-accent/50">
                    <td className="px-4 py-3 font-mono text-xs">{task.taskId?.slice(0, 8)}...</td>
                    <td className="px-4 py-3 font-mono text-xs">{task.productId?.slice(0, 8)}...</td>
                    <td className="px-4 py-3">{task.videoType}</td>
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
                    <td className="px-4 py-3">{task.duration}s</td>
                    <td className="px-4 py-3 text-xs text-destructive max-w-[200px] truncate">
                      {task.errorMessage || "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
