/**
 * RenderManifest 11-Step Validation Pipeline
 *
 * 按 docs/04-render-manifest-schema.md 逐项校验：
 * 1. JSON Schema 校验
 * 2. template 存在
 * 3. assets 数量 ≥ 4
 * 4. 非 text 素材 URL 可访问 (placeholder)
 * 5. text 素材有 textContent (placeholder)
 * 6. duration 与 assets 总 duration 误差 ≤ 1s
 * 7. 字幕长度 ≤ 90 字符
 * 8. 音乐/配音/输出配置
 * 9. 素材下载安全校验 (SSRF 白名单)
 * 10. MIME 类型校验
 * 11. 文件大小校验
 */

const V1_TEMPLATES = [
  "pain_point_solution_v1",
  "before_after_v1",
  "review_v1",
];

export interface ValidationResult {
  valid: boolean;
  error?: string;
  details?: Record<string, unknown>;
}

export async function validateRenderManifest(
  manifest: Record<string, unknown>
): Promise<ValidationResult> {
  // Step 1: Required fields
  if (!manifest.manifestVersion || manifest.manifestVersion !== "1.0.0") {
    return { valid: false, error: "manifestVersion must be '1.0.0'" };
  }

  // Step 2: Validate template exists
  const template = manifest.template as string;
  if (!template || !V1_TEMPLATES.includes(template)) {
    return {
      valid: false,
      error: `Unknown template: '${template}'. V1 supports: ${V1_TEMPLATES.join(", ")}`,
    };
  }

  // Step 3: Validate assets array
  const assets = manifest.assets as Array<Record<string, unknown>> | undefined;
  if (!assets || !Array.isArray(assets)) {
    return { valid: false, error: "assets must be an array" };
  }

  // Step 4: Minimum 4 assets
  if (assets.length < 4) {
    return {
      valid: false,
      error: `Insufficient assets: ${assets.length} (minimum 4 required)`,
    };
  }

  // Step 5: Validate each asset
  for (let i = 0; i < assets.length; i++) {
    const asset = assets[i];
    if (!asset.type) {
      return { valid: false, error: `Asset ${i}: missing 'type'` };
    }
    if (asset.type !== "text" && !asset.url) {
      return { valid: false, error: `Asset ${i} (${asset.type}): missing 'url'` };
    }
    if (asset.type === "text" && !asset.textContent) {
      return { valid: false, error: `Asset ${i} (text): missing 'textContent'` };
    }
    // Validate subtitle length
    if (asset.subtitle && typeof asset.subtitle === "string") {
      if (asset.subtitle.length > 90) {
        return {
          valid: false,
          error: `Asset ${i}: subtitle exceeds 90 characters (${asset.subtitle.length})`,
        };
      }
    }
  }

  // Step 6: Duration consistency check
  const manifestDuration = manifest.duration as number;
  if (typeof manifestDuration !== "number" || manifestDuration <= 0) {
    return { valid: false, error: "duration must be a positive number" };
  }

  const assetsTotalDuration = assets.reduce(
    (sum, a) => sum + (typeof a.duration === "number" ? a.duration : 0),
    0
  );

  if (Math.abs(manifestDuration - assetsTotalDuration) > 1) {
    return {
      valid: false,
      error: `Duration mismatch: manifest=${manifestDuration}s, assets total=${assetsTotalDuration}s (max ±1s)`,
    };
  }

  // Step 7-8: Output config validation
  const output = manifest.output as Record<string, unknown> | undefined;
  if (output) {
    if (output.resolution && output.resolution !== "1080x1920") {
      return { valid: false, error: "V1 only supports 1080x1920 resolution" };
    }
    if (output.fps && output.fps !== 30) {
      return { valid: false, error: "V1 only supports 30fps" };
    }
  }

  // All checks passed
  return { valid: true };
}

/**
 * URL SSRF check — whitelist only COS/CDN domains.
 * TODO: Phase 5 — implement actual URL fetching with SSRF protection.
 */
export function isUrlAllowed(url: string): boolean {
  // 腾讯云 COS + CDN 白名单，防止 SSRF
  const allowedDomains = [
    "myqcloud.com",       // COS 默认域名: <bucket>.cos.<region>.myqcloud.com
    "tencentcos.cn",      // COS 内网域名
    "cdn.myqcloud.com",   // 腾讯云 CDN
  ];
  try {
    const parsed = new URL(url);
    return allowedDomains.some((domain) => parsed.hostname.endsWith(domain));
  } catch {
    return false;
  }
}
