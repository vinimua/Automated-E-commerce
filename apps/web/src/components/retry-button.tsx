"use client";

import { apiRequest } from "@/lib/api-client";
import { useState } from "react";
import { RefreshCw, AlertCircle } from "lucide-react";

interface RetryButtonProps {
  taskId: string;
  errorMessage?: string | null;
  errorRetryable?: boolean | null;
  onRetry?: () => void;
}

export function RetryButton({ taskId, errorMessage, errorRetryable, onRetry }: RetryButtonProps) {
  const [retrying, setRetrying] = useState(false);
  const [localError, setLocalError] = useState("");

  async function handleRetry() {
    setRetrying(true);
    setLocalError("");
    try {
      const res = await apiRequest<{ code: number; message: string }>(
        `/api/video-tasks/${taskId}/retry`,
        { method: "POST" }
      );
      if (res.code === 0) {
        onRetry?.();
      } else {
        setLocalError(res.message || "重试失败");
      }
    } catch (e: any) {
      setLocalError(e.message || "网络错误");
    } finally {
      setRetrying(false);
    }
  }

  if (errorRetryable === false) {
    return (
      <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4">
        <div className="flex items-start gap-3">
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
          <div>
            <p className="font-medium text-destructive">任务失败</p>
            {errorMessage && <p className="mt-1 text-sm text-muted-foreground">{errorMessage}</p>}
            <p className="mt-2 text-xs text-muted-foreground">此错误不可重试，请联系管理员</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4">
      <div className="flex items-start gap-3">
        <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-destructive" />
        <div className="flex-1">
          <p className="font-medium text-destructive">任务失败</p>
          {errorMessage && <p className="mt-1 text-sm text-muted-foreground">{errorMessage}</p>}
          {localError && <p className="mt-1 text-sm text-destructive">{localError}</p>}
          <button
            onClick={handleRetry}
            disabled={retrying}
            className="mt-3 inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${retrying ? "animate-spin" : ""}`} />
            {retrying ? "重试中..." : "重新尝试"}
          </button>
        </div>
      </div>
    </div>
  );
}
