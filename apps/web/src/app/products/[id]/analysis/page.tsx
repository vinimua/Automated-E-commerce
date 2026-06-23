"use client";

import { apiRequest } from "@/lib/api-client";
import type { Product } from "@/types/api";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";

export default function ProductAnalysisPage() {
  const { id } = useParams<{ id: string }>();
  const [product, setProduct] = useState<Product | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      try {
        const res = await apiRequest<{ code: number; message: string; data: Product }>(`/api/products/${id}`);
        if (res.code === 0) setProduct(res.data);
        else setError(res.message || "加载失败");
      } catch (e: any) {
        setError(e.message || "网络错误");
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [id]);

  if (loading) {
    return <div className="flex h-96 items-center justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" /></div>;
  }
  if (error) {
    return <div className="p-8"><p className="text-destructive">{error}</p></div>;
  }
  if (!product) return null;

  const hasAnalysis =
    product.category ||
    product.sellingPoints?.length ||
    product.painPoints?.length ||
    product.targetAudience?.length ||
    product.scenes?.length ||
    product.videoScore != null ||
    product.riskTips?.length;

  return (
    <div className="mx-auto max-w-3xl space-y-8 p-8">
      <div>
        <Link href="/dashboard" className="text-sm text-muted-foreground hover:text-foreground">← 返回工作台</Link>
        <h1 className="mt-2 text-2xl font-bold">{product.name}</h1>
        <p className="text-sm text-muted-foreground">AI 分析结果</p>
      </div>

      {/* Product images */}
      {product.imageUrls && product.imageUrls.length > 0 && (
        <div className="flex gap-2 overflow-auto">
          {product.imageUrls.map((url, i) => (
            <img key={i} src={url} alt={`${product.name} ${i + 1}`} className="h-32 w-32 rounded-lg border object-cover" />
          ))}
        </div>
      )}

      {/* AI Analysis */}
      {hasAnalysis ? (
        <div className="rounded-lg border bg-card p-6 space-y-4">
          <h2 className="font-semibold">商品分析</h2>

          {product.category && (
            <Section title="类目" items={[product.category]} />
          )}
          {product.sellingPoints && product.sellingPoints.length > 0 && (
            <Section title="卖点" items={product.sellingPoints} />
          )}
          {product.painPoints && product.painPoints.length > 0 && (
            <Section title="用户痛点" items={product.painPoints} />
          )}
          {product.targetAudience && product.targetAudience.length > 0 && (
            <Section title="目标受众" items={product.targetAudience} />
          )}
          {product.scenes && product.scenes.length > 0 && (
            <Section title="使用场景" items={product.scenes} />
          )}
          {product.videoScore != null && (
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground">视频潜力分</span>
              <span className="font-semibold text-primary">{product.videoScore}/100</span>
            </div>
          )}
          {product.riskTips && product.riskTips.length > 0 && (
            <div className="rounded-md border border-yellow-200 bg-yellow-50 p-3">
              <p className="text-sm font-medium text-yellow-800">合规提示</p>
              <ul className="mt-1 list-inside list-disc text-sm text-yellow-700">
                {product.riskTips.map((tip, i) => <li key={i}>{tip}</li>)}
              </ul>
            </div>
          )}
        </div>
      ) : (
        <div className="rounded-lg border bg-card p-6">
          <h2 className="font-semibold">商品分析</h2>
          <p className="mt-2 text-sm text-muted-foreground">分析结果尚未生成，请稍后返回查看。</p>
        </div>
      )}

      {/* Action */}
      <Link
        href="/products/new"
        className="inline-flex items-center gap-2 rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90"
      >
        创建新视频 →
      </Link>
    </div>
  );
}

function Section({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <p className="text-sm font-medium text-muted-foreground">{title}</p>
      <div className="mt-1 flex flex-wrap gap-1.5">
        {items.map((item, i) => (
          <span key={i} className="rounded-full bg-accent px-2.5 py-0.5 text-xs">{item}</span>
        ))}
      </div>
    </div>
  );
}
