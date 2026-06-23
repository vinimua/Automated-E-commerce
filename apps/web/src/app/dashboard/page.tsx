"use client";

import { apiRequest } from "@/lib/api-client";
import { TaskProgress } from "@/components/task-progress";
import { STATUS_LABELS, VIDEO_TYPE_LABELS } from "@/types/api";
import type { UserQuota, VideoTask } from "@/types/api";
import { PlusCircle, Video, ImageIcon, Download } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

interface QuotaCardProps {
  label: string; used: number; total: number; icon: React.ReactNode;
}
function QuotaCard({ label, used, total, icon }: QuotaCardProps) {
  const pct = total > 0 ? Math.round((used / total) * 100) : 0;
  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="flex items-center justify-between">
        <span className="text-sm text-muted-foreground">{label}</span>
        <span className="text-muted-foreground">{icon}</span>
      </div>
      <p className="mt-1 text-2xl font-semibold">{used}<span className="text-base font-normal text-muted-foreground">/{total}</span></p>
      <div className="mt-2 h-1.5 rounded-full bg-muted">
        <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${Math.min(pct, 100)}%` }} />
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const [tasks, setTasks] = useState<VideoTask[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function load() {
      try {
        const [quotaRes, taskRes] = await Promise.all([
          apiRequest<{ code: number; message: string; data: UserQuota }>("/api/quotas/me"),
          apiRequest<{ code: number; message: string; data: { items: VideoTask[]; total: number } }>("/api/video-tasks?pageSize=5"),
        ]);
        if (quotaRes.code === 0) setQuota(quotaRes.data);
        if (taskRes.code === 0) setTasks(taskRes.data.items || []);
      } catch (e) { /* silently ignore */ }
      finally { setLoading(false); }
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

  return (
    <div className="space-y-8 p-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">工作台</h1>
          <p className="text-sm text-muted-foreground">AI 驱动的 TikTok 带货视频生成</p>
        </div>
        <Link
          href="/products/new"
          className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          <PlusCircle className="h-4 w-4" />
          新建视频
        </Link>
      </div>

      {/* Quota */}
      <div className="grid grid-cols-4 gap-4">
        <QuotaCard label="视频生成" used={quota?.usedVideoCount ?? 0} total={quota?.videoQuota ?? 0} icon={<Video className="h-4 w-4" />} />
        <QuotaCard label="AI 图片" used={quota?.usedImageCount ?? 0} total={quota?.imageQuota ?? 0} icon={<ImageIcon className="h-4 w-4" />} />
        <QuotaCard label="AI 片段" used={quota?.usedVideoClipCount ?? 0} total={quota?.videoClipQuota ?? 0} icon={<Video className="h-4 w-4" />} />
        <QuotaCard label="导出" used={quota?.usedExportCount ?? 0} total={quota?.exportQuota ?? 0} icon={<Download className="h-4 w-4" />} />
      </div>

      {/* Recent tasks */}
      <div>
        <h2 className="mb-4 text-lg font-semibold">最近任务</h2>
        {tasks.length === 0 ? (
          <div className="rounded-lg border bg-card p-12 text-center">
            <p className="text-muted-foreground">还没有视频任务</p>
            <Link href="/products/new" className="mt-2 inline-block text-sm font-medium text-primary hover:underline">
              创建第一个 →
            </Link>
          </div>
        ) : (
          <div className="space-y-3">
            {tasks.map((task) => (
              <Link
                key={task.taskId}
                href={`/video-tasks/${task.taskId}/plans`}
                className="block rounded-lg border bg-card p-4 transition-colors hover:bg-accent/50"
              >
                <div className="flex items-center justify-between">
                  <div>
                    <p className="font-medium">
                      {VIDEO_TYPE_LABELS[task.videoType] || task.videoType}
                      <span className="ml-2 text-sm text-muted-foreground">{task.duration}s</span>
                    </p>
                    <p className="mt-0.5 text-sm text-muted-foreground">
                      {STATUS_LABELS[task.status] || task.status}
                      {task.errorMessage && (
                        <span className="ml-2 text-destructive">— {task.errorMessage}</span>
                      )}
                    </p>
                  </div>
                  <div className="text-right">
                    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${
                      task.status === "completed" ? "bg-green-100 text-green-700" :
                      task.status === "failed" ? "bg-red-100 text-red-700" :
                      "bg-blue-100 text-blue-700"
                    }`}>
                      {task.progress}%
                    </span>
                  </div>
                </div>
                {["analyzing", "rendering", "script_generating", "material_generating"].includes(task.status) && (
                  <div className="mt-3">
                    <TaskProgress status={task.status} errorMessage={task.errorMessage} />
                  </div>
                )}
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
