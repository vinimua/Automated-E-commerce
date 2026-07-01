"use client";

import { apiRequest } from "@/lib/api-client";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { XCircle } from "lucide-react";

interface CancelTaskButtonProps {
  taskId: string;
  disabled?: boolean;
  className?: string;
}

export function CancelTaskButton({ taskId, disabled, className }: CancelTaskButtonProps) {
  const router = useRouter();
  const [cancelling, setCancelling] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  async function handleCancel() {
    setCancelling(true);
    try {
      const res = await apiRequest<{ code: number; message: string }>(
        `/api/video-tasks/${taskId}/cancel`,
        { method: "POST" }
      );
      if (res.code === 0) {
        router.push("/dashboard");
      }
    } catch {
      // silently fail, user can retry
    } finally {
      setCancelling(false);
      setShowConfirm(false);
    }
  }

  if (showConfirm) {
    return (
      <div className={`inline-flex items-center gap-2 ${className || ""}`}>
        <span className="text-sm text-destructive font-medium">确认取消？</span>
        <button
          onClick={handleCancel}
          disabled={cancelling}
          className="rounded-md bg-destructive px-3 py-1.5 text-xs font-medium text-destructive-foreground hover:bg-destructive/90 disabled:opacity-50"
        >
          {cancelling ? "取消中..." : "确认"}
        </button>
        <button
          onClick={() => setShowConfirm(false)}
          className="rounded-md border px-3 py-1.5 text-xs hover:bg-accent"
        >
          返回
        </button>
      </div>
    );
  }

  return (
    <button
      onClick={() => setShowConfirm(true)}
      disabled={disabled}
      className={`inline-flex items-center gap-1.5 rounded-md border border-destructive/30 px-3 py-1.5 text-xs font-medium text-destructive hover:bg-destructive/5 disabled:opacity-50 ${className || ""}`}
    >
      <XCircle className="h-3.5 w-3.5" />
      取消任务
    </button>
  );
}
