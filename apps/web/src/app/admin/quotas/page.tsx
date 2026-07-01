"use client";

import { apiRequest } from "@/lib/api-client";
import type { AdminQuotaItem, AdminQuotaListData, AdminQuotaUpdateRequest } from "@/types/api";
import { Save } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

type DraftQuota = Required<Pick<
  AdminQuotaUpdateRequest,
  "videoQuota" | "imageQuota" | "videoClipQuota" | "exportQuota"
>>;

const quotaFields: Array<{ key: keyof DraftQuota; label: string; usedKey: keyof AdminQuotaItem }> = [
  { key: "videoQuota", label: "Video", usedKey: "usedVideoCount" },
  { key: "imageQuota", label: "Image", usedKey: "usedImageCount" },
  { key: "videoClipQuota", label: "Clip", usedKey: "usedVideoClipCount" },
  { key: "exportQuota", label: "Export", usedKey: "usedExportCount" },
];

function toDraft(item: AdminQuotaItem): DraftQuota {
  return {
    videoQuota: item.videoQuota ?? 0,
    imageQuota: item.imageQuota ?? 0,
    videoClipQuota: item.videoClipQuota ?? 0,
    exportQuota: item.exportQuota ?? 0,
  };
}

export default function AdminQuotasPage() {
  const [items, setItems] = useState<AdminQuotaItem[]>([]);
  const [drafts, setDrafts] = useState<Record<string, DraftQuota>>({});
  const [savingUserId, setSavingUserId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);

  const load = useCallback(async (pageNum: number) => {
    setLoading(true);
    setError("");
    try {
      const res = await apiRequest<{
        code: number;
        message: string;
        data: AdminQuotaListData;
      }>(`/api/admin/quotas?page=${pageNum}&pageSize=20`);

      if (res.code === 0 && res.data) {
        const nextItems = res.data.items || [];
        setItems(nextItems);
        setDrafts(Object.fromEntries(
          nextItems
            .filter((item) => item.userId)
            .map((item) => [item.userId as string, toDraft(item)])
        ));
        setTotal(res.data.total || 0);
        setTotalPages(res.data.totalPages || 1);
      } else {
        setError(res.message || "Failed to load quotas");
      }
    } catch (e: any) {
      setError(e.message || "Failed to load quotas");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(page);
  }, [load, page]);

  function updateDraft(userId: string, key: keyof DraftQuota, value: string) {
    const nextValue = Math.max(0, Number.parseInt(value || "0", 10));
    setDrafts((prev) => ({
      ...prev,
      [userId]: {
        ...prev[userId],
        [key]: Number.isFinite(nextValue) ? nextValue : 0,
      },
    }));
  }

  async function saveQuota(userId: string) {
    const draft = drafts[userId];
    if (!draft) return;
    setSavingUserId(userId);
    setError("");
    try {
      const res = await apiRequest<{
        code: number;
        message: string;
        data: AdminQuotaItem;
      }>(`/api/admin/quotas/${userId}`, {
        method: "PATCH",
        body: draft,
      });

      if (res.code === 0 && res.data) {
        setItems((prev) => prev.map((item) => item.userId === userId ? res.data : item));
        setDrafts((prev) => ({ ...prev, [userId]: toDraft(res.data) }));
      } else {
        setError(res.message || "Failed to save quota");
      }
    } catch (e: any) {
      setError(e.message || "Failed to save quota");
    } finally {
      setSavingUserId(null);
    }
  }

  return (
    <div className="space-y-6 p-8">
      <div>
        <h1 className="text-2xl font-bold">Quota Management</h1>
        <p className="text-sm text-muted-foreground">Daily quota limits for all users · {total} total</p>
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
              <table className="w-full min-w-[1120px] text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="px-4 py-3 text-left font-medium">User</th>
                    <th className="px-4 py-3 text-left font-medium">Status</th>
                    {quotaFields.map((field) => (
                      <th key={field.key} className="px-4 py-3 text-left font-medium">
                        {field.label}
                      </th>
                    ))}
                    <th className="px-4 py-3 text-left font-medium">Quota Date</th>
                    <th className="px-4 py-3 text-left font-medium">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y">
                  {items.map((item) => {
                    const userId = item.userId || "";
                    const draft = drafts[userId] || toDraft(item);
                    return (
                      <tr key={userId} className="hover:bg-accent/50">
                        <td className="px-4 py-3">
                          <p className="font-medium">{item.email || "-"}</p>
                          <p className="font-mono text-xs text-muted-foreground">{userId.slice(0, 8)}...</p>
                        </td>
                        <td className="px-4 py-3">
                          <span className="inline-flex rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                            {item.status || "-"}
                          </span>
                        </td>
                        {quotaFields.map((field) => (
                          <td key={field.key} className="px-4 py-3">
                            <div className="flex items-center gap-2">
                              <span className="w-10 text-xs text-muted-foreground">
                                {Number(item[field.usedKey] || 0)}
                              </span>
                              <span className="text-xs text-muted-foreground">/</span>
                              <input
                                type="number"
                                min={0}
                                value={draft[field.key]}
                                onChange={(event) => updateDraft(userId, field.key, event.target.value)}
                                className="h-8 w-20 rounded-md border px-2 text-sm focus:border-primary focus:outline-none"
                              />
                            </div>
                          </td>
                        ))}
                        <td className="px-4 py-3 text-muted-foreground">{item.quotaDate || "-"}</td>
                        <td className="px-4 py-3">
                          <button
                            onClick={() => saveQuota(userId)}
                            disabled={!userId || savingUserId === userId}
                            className="inline-flex items-center gap-2 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                          >
                            <Save className="h-4 w-4" />
                            {savingUserId === userId ? "Saving" : "Save"}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
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
