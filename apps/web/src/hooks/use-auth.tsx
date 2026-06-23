"use client";

import { useRouter } from "next/navigation";
import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { apiRequest, setTokens, clearTokens } from "@/lib/api-client";

interface AuthState {
  userId: string | null;
  email: string | null;
  role: string | null;
  isLoggedIn: boolean;
  isLoading: boolean;
}

interface AuthContextType extends AuthState {
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);
const AUTH_EXPIRED_EVENT = "tk-auth-expired";

function decodeJwtPayload(access: string): { sub?: string; email?: string; exp?: number; role?: string } {
  return JSON.parse(atob(access.split(".")[1]));
}

function isExpired(exp?: number) {
  return typeof exp === "number" && exp * 1000 <= Date.now();
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [state, setState] = useState<AuthState>({
    userId: null, email: null, role: null, isLoggedIn: false, isLoading: true,
  });

  // Check if user is already logged in (tokens in localStorage)
  useEffect(() => {
    const access = localStorage.getItem("accessToken");
    if (access) {
      // Decode JWT to get user info (without verifying — verification is server-side)
      try {
        const payload = decodeJwtPayload(access);
        if (isExpired(payload.exp)) {
          clearTokens();
          setState((s) => ({ ...s, isLoading: false }));
          return;
        }
        setState({
          userId: payload.sub ?? null,
          email: payload.email ?? null,
          role: payload.role ?? null,
          isLoggedIn: true,
          isLoading: false,
        });
      } catch {
        clearTokens();
        setState((s) => ({ ...s, isLoading: false }));
      }
    } else {
      setState((s) => ({ ...s, isLoading: false }));
    }
  }, []);

  useEffect(() => {
    function handleAuthExpired() {
      clearTokens();
      setState({ userId: null, email: null, role: null, isLoggedIn: false, isLoading: false });
      router.push("/login");
    }

    window.addEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleAuthExpired);
  }, [router]);

  const login = useCallback(async (email: string, password: string) => {
    const res = await apiRequest<{ code: number; message: string; data: { userId: string; accessToken: string; refreshToken: string } }>(
      "/api/auth/login", { method: "POST", body: { email, password }, skipAuth: true }
    );
    if (res.code === 0 && res.data) {
      setTokens(res.data.accessToken, res.data.refreshToken);
      const loginPayload = decodeJwtPayload(res.data.accessToken);
      setState({ userId: res.data.userId, email, role: loginPayload.role ?? null, isLoggedIn: true, isLoading: false });
      router.push("/dashboard");
    }
  }, [router]);

  const register = useCallback(async (email: string, password: string) => {
    const res = await apiRequest<{ code: number; message: string; data: { userId: string; accessToken: string; refreshToken: string } }>(
      "/api/auth/register", { method: "POST", body: { email, password }, skipAuth: true }
    );
    if (res.code === 0 && res.data) {
      setTokens(res.data.accessToken, res.data.refreshToken);
      const regPayload = decodeJwtPayload(res.data.accessToken);
      setState({ userId: res.data.userId, email, role: regPayload.role ?? null, isLoggedIn: true, isLoading: false });
      router.push("/dashboard");
    }
  }, [router]);

  const logout = useCallback(() => {
    clearTokens();
    setState({ userId: null, email: null, role: null, isLoggedIn: false, isLoading: false });
    router.push("/login");
  }, [router]);

  return (
    <AuthContext.Provider value={{ ...state, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
