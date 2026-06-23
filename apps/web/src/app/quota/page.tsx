"use client";

import { apiRequest } from "@/lib/api-client";
import type { UserQuota } from "@/types/api";
import { useEffect, useState } from "react";
import { Video, ImageIcon, Film, Download, BarChart3 } from "lucide-react";

interface QuotaCardProps {
  label: string;
  description: string;
  used: number;
  total: number;
  icon: React.ReactNode;
}

function QuotaCard({ label, description, used, total, icon }: QuotaCardProps) {
  const pct = total > 0 ? Math.round((used / total) * 100) : 0;
  const isLow = total > 0 && used >= total;
  const isWarning = total > 0 && used >= total * 0.8 && !isLow;

  return (
    <div className={`rounded-lg border p-6 ${isLow ? "border-destructive/50 bg-destructive/5" : isWarning ? "border-yellow-500/50 bg-yellow-50" : "bg-card"}`}>
      <div className="flex items-center justify-between">
        <div>
          <span className="text-sm font-medium">{label}</span>
          <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
        </div>
        <span className="text-muted-foreground">{icon}</span>
      </div>
      <p className="mt-3 text-3xl font-semibold">
        {used}
        <span className="text-lg font-normal text-muted-foreground"> / {total}</span>
      </p>
      <div className="mt-3 h-2 rounded-full bg-muted">
        <div
          className={`h-full rounded-full transition-all ${
            isLow ? "bg-destructive" : isWarning ? "bg-yellow-500" : "bg-primary"
          }`}
          style={{ width: `${Math.min(pct, 100)}%` }}
        />
      </div>
      <p className={`mt-2 text-xs ${isLow ? "text-destructive" : isWarning ? "text-yellow-600" : "text-muted-foreground"}`}>
        {isLow ? "已用完" : isWarning ? `剩余 ${total - used} 次` : `已用 ${pct}%`}
      </p>
    </div>
  );
}

export default function QuotaPage() {
  const [quota, setQuota] = useState<UserQuota | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const res = await apiRequest<{ code: number; message: string; data: UserQuota }>(
          "/api/quotas/me"
        );
        if (res.code === 0) setQuota(res.data);
        else setError(res.message || "加载失败");
      } catch (e: any) {
        setError(e.message || "网络错误");
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

  return (
    <div className="space-y-8 p-8">
      <div>
        <h1 className="text-2xl font-bold">额度使用情况</h1>
        <p className="text-sm text-muted-foreground">当前套餐的资源使用概览</p>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      {quota && (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <QuotaCard
            label="视频生成"
            description="创建并渲染完整带货视频"
            used={quota.usedVideoCount ?? 0}
            total={quota.videoQuota ?? 0}
            icon={<Video className="h-5 w-5" />}
          />
          <QuotaCard
            label="AI 图片素材"
            description="AI 生成的商品图片素材"
            used={quota.usedImageCount ?? 0}
            total={quota.imageQuota ?? 0}
            icon={<ImageIcon className="h-5 w-5" />}
          />
          <QuotaCard
            label="AI 视频片段"
            description="AI 生成的短视频片段"
            used={quota.usedVideoClipCount ?? 0}
            total={quota.videoClipQuota ?? 0}
            icon={<Film className="h-5 w-5" />}
          />
          <QuotaCard
            label="视频导出"
            description="导出视频到本地/分享"
            used={quota.usedExportCount ?? 0}
            total={quota.exportQuota ?? 0}
            icon={<Download className="h-5 w-5" />}
          />
        </div>
      )}

      {/* Summary bar */}
      {quota && (
        <div className="rounded-lg border bg-card p-6">
          <h3 className="text-sm font-semibold flex items-center gap-2">
            <BarChart3 className="h-4 w-4" />
            使用概览
          </h3>
          <div className="mt-4 grid grid-cols-2 gap-4 text-sm sm:grid-cols-4">
            {[
              { label: "视频生成", used: quota.usedVideoCount ?? 0, total: quota.videoQuota ?? 0 },
              { label: "AI 图片", used: quota.usedImageCount ?? 0, total: quota.imageQuota ?? 0 },
              { label: "AI 片段", used: quota.usedVideoClipCount ?? 0, total: quota.videoClipQuota ?? 0 },
              { label: "导出", used: quota.usedExportCount ?? 0, total: quota.exportQuota ?? 0 },
            ].map((item) => (
              <div key={item.label} className="text-center">
                <p className="text-2xl font-bold">
                  {item.used}
                  <span className="text-sm font-normal text-muted-foreground">/{item.total}</span>
                </p>
                <p className="text-xs text-muted-foreground">{item.label}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
