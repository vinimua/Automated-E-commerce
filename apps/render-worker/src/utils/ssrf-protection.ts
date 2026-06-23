/**
 * SSRF protection — domain whitelist and private IP rejection.
 *
 * The Render Worker downloads assets from URLs in the RenderManifest.
 * We MUST prevent attackers from crafting manifests that cause the
 * worker to fetch internal resources (metadata services, localhost, etc.).
 */
import { lookup } from "dns/promises";
import { isIP } from "net";

/** Allowed domains for asset downloads (Tencent COS + CDN) */
const ALLOWED_DOMAINS = [
  "myqcloud.com",
  "tencentcos.cn",
  "cdn.myqcloud.com",
];

/** Private IPv4 ranges (RFC 1918) + localhost + link-local */
const PRIVATE_IPV4_PATTERNS = [
  /^127\./,                        // loopback
  /^10\./,                         // Class A private
  /^172\.(1[6-9]|2\d|3[01])\./,   // Class B private
  /^192\.168\./,                   // Class C private
  /^169\.254\./,                   // link-local (including AWS metadata)
  /^0\.0\.0\.0$/,                  // null address
];

/** Private IPv6 addresses */
const PRIVATE_IPV6 = ["::1", "fc00::", "fd00::", "fe80::"];

/**
 * Check if a URL's domain is in the COS/CDN whitelist.
 */
export function isDomainAllowed(urlString: string): boolean {
  try {
    const parsed = new URL(urlString);
    return ALLOWED_DOMAINS.some(
      (domain) =>
        parsed.hostname === domain || parsed.hostname.endsWith("." + domain)
    );
  } catch {
    return false;
  }
}

/**
 * Check if an IP address falls in any private range.
 * This is a defense-in-depth measure: even if the domain check somehow
 * allows a private address, this second check blocks it.
 */
export function isPrivateIp(hostname: string): boolean {
  if (!hostname) return true;

  // IPv4 private ranges
  for (const pattern of PRIVATE_IPV4_PATTERNS) {
    if (pattern.test(hostname)) return true;
  }

  // IPv6 private ranges
  const lower = hostname.toLowerCase();
  for (const prefix of PRIVATE_IPV6) {
    if (lower.startsWith(prefix)) return true;
  }

  return false;
}

/**
 * Validate a URL is safe to fetch.
 * Returns the parsed URL if safe, or null if blocked.
 */
export function validateUrl(urlString: string): URL | null {
  let parsed: URL;
  try {
    parsed = new URL(urlString);
  } catch {
    return null;
  }

  // Only allow HTTPS
  if (parsed.protocol !== "https:") {
    return null;
  }

  // Reject non-standard ports
  if (parsed.port && parsed.port !== "443") {
    return null;
  }

  // SSRF: reject private IPs
  if (isPrivateIp(parsed.hostname)) {
    return null;
  }

  // Domain whitelist
  if (!isDomainAllowed(urlString)) {
    return null;
  }

  return parsed;
}

/**
 * Validate URL syntax/domain and verify resolved DNS answers are public IPs.
 * This closes the gap where a whitelisted hostname could resolve to a private
 * address through DNS rebinding or a misconfigured internal DNS record.
 */
export async function validateResolvedUrl(urlString: string): Promise<URL | null> {
  const parsed = validateUrl(urlString);
  if (!parsed) return null;

  if (isIP(parsed.hostname)) {
    return isPrivateIp(parsed.hostname) ? null : parsed;
  }

  try {
    const answers = await lookup(parsed.hostname, { all: true, verbatim: true });
    if (answers.length === 0) return null;
    if (answers.some((answer) => isPrivateIp(answer.address))) return null;
    return parsed;
  } catch {
    return null;
  }
}
