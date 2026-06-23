"use client";

import { useAuth } from "@/hooks/use-auth";
import { cn } from "@/lib/utils";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import {
  Activity,
  ArrowLeft,
  FileText,
  Film,
  LayoutDashboard,
  Shield,
  Users,
  Video,
} from "lucide-react";

const adminLinks = [
  { href: "/admin", label: "Dashboard", icon: LayoutDashboard },
  { href: "/admin/users", label: "Users", icon: Users },
  { href: "/admin/video-tasks", label: "Tasks", icon: Video },
  { href: "/admin/videos", label: "Videos", icon: Film },
  { href: "/admin/model-logs", label: "AI Logs", icon: FileText },
  { href: "/admin/render-logs", label: "Render Logs", icon: Activity },
];

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const { role, isLoading } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && role !== "ADMIN") {
      router.push("/dashboard");
    }
  }, [role, isLoading, router]);

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (role !== "ADMIN") {
    return null;
  }

  return (
    <div className="min-h-screen bg-background">
      <div className="border-b bg-card">
        <div className="flex h-12 items-center gap-4 px-6">
          <Link
            href="/dashboard"
            className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Back to dashboard
          </Link>
          <span className="text-muted-foreground">/</span>
          <span className="flex items-center gap-1 text-sm font-medium">
            <Shield className="h-4 w-4 text-primary" />
            Admin
          </span>
        </div>
      </div>

      <div className="flex">
        <aside className="min-h-[calc(100vh-3rem)] w-52 shrink-0 border-r bg-card">
          <nav className="space-y-0.5 p-3">
            {adminLinks.map((item) => {
              const isActive = item.href === "/admin"
                ? pathname === "/admin"
                : pathname.startsWith(item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={cn(
                    "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                    isActive
                      ? "bg-primary/10 font-medium text-primary"
                      : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                  )}
                >
                  <Icon className="h-4 w-4" />
                  {item.label}
                </Link>
              );
            })}
          </nav>
        </aside>

        <main className="flex-1">{children}</main>
      </div>
    </div>
  );
}
