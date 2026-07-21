// V1 API Client — wraps fetch with JWT auth and auto-refresh
// Full implementation in Phase 3.

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8099";

type RequestOptions = {
  method?: string;
  body?: unknown;
  headers?: Record<string, string>;
  skipAuth?: boolean;
};

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public rawError?: unknown
  ) {
    super(message);
    this.name = "ApiError";
  }
}

let accessToken: string | null = null;
let refreshToken: string | null = null;
const AUTH_EXPIRED_EVENT = "tk-auth-expired";

export function setTokens(access: string, refresh: string) {
  accessToken = access;
  refreshToken = refresh;
  if (typeof window !== "undefined") {
    localStorage.setItem("accessToken", access);
    localStorage.setItem("refreshToken", refresh);
  }
}

export function clearTokens() {
  accessToken = null;
  refreshToken = null;
  if (typeof window !== "undefined") {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("refreshToken");
  }
}

function notifyAuthExpired() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
  }
}

async function refreshAccessToken(): Promise<boolean> {
  if (!refreshToken) return false;
  try {
    const res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) return false;
    const json = await res.json();
    if (json.data?.accessToken) {
      setTokens(json.data.accessToken, json.data.refreshToken || refreshToken);
      return true;
    }
    return false;
  } catch {
    return false;
  }
}

export async function apiRequest<T = unknown>(
  path: string,
  opts: RequestOptions = {}
): Promise<T> {
  const { method = "GET", body, headers = {}, skipAuth = false } = opts;

  const fetchHeaders: Record<string, string> = {
    "Content-Type": "application/json",
    ...headers,
  };

  if (!skipAuth) {
    // Restore from localStorage on first call
    if (!accessToken && typeof window !== "undefined") {
      accessToken = localStorage.getItem("accessToken");
      refreshToken = localStorage.getItem("refreshToken");
    }
    if (accessToken) {
      fetchHeaders["Authorization"] = `Bearer ${accessToken}`;
    }
  }

  const url = `${API_BASE}${path}`;
  let res = await fetch(url, {
    method,
    headers: fetchHeaders,
    body: body ? JSON.stringify(body) : undefined,
  });

  // Auto-refresh on 401
  if (res.status === 401 && !skipAuth) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      fetchHeaders["Authorization"] = `Bearer ${accessToken}`;
      res = await fetch(url, {
        method,
        headers: fetchHeaders,
        body: body ? JSON.stringify(body) : undefined,
      });
    } else {
      clearTokens();
      notifyAuthExpired();
    }
  }

  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new ApiError(res.status, errorBody.message || res.statusText, errorBody);
  }

  return res.json();
}

/** Upload a file via multipart/form-data. Returns the parsed JSON response. */
export async function uploadFile<T = { fileUrl: string }>(
  path: string,
  formData: FormData
): Promise<T> {
  if (!accessToken && typeof window !== "undefined") {
    accessToken = localStorage.getItem("accessToken");
    refreshToken = localStorage.getItem("refreshToken");
  }

  const headers: Record<string, string> = {};
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  const url = `${API_BASE}${path}`;
  let res = await fetch(url, {
    method: "POST",
    headers,
    body: formData,
  });

  if (res.status === 401) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      headers["Authorization"] = `Bearer ${accessToken}`;
      res = await fetch(url, { method: "POST", headers, body: formData });
    } else {
      clearTokens();
      notifyAuthExpired();
    }
  }

  if (!res.ok) {
    const errorBody = await res.json().catch(() => ({}));
    throw new ApiError(res.status, errorBody.message || res.statusText, errorBody);
  }

  return res.json();
}
