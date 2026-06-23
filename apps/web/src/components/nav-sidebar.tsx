"use client";

import { useAuth } from "@/hooks/use-auth";
import { cn } from "@/lib/utils";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Activity,
  BarChart3,
  FileText,
  Film,
  LayoutDashboard,
  PlusCircle,
  Shield,
  Users,
  Video,
} from "lucide-react";

const userNavItems = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/products/new", label: "New Video", icon: PlusCircle },
  { href: "/videos", label: "Video Library", icon: Film },
  { href: "/quota", label: "Quota", icon: BarChart3 },
];

const adminNavItems = [
  { href: "/admin", label: "Admin Dashboard", icon: Shield },
  { href: "/admin/users", label: "User Management", icon: Users },
  { href: "/admin/video-tasks", label: "Task Management", icon: Video },
  { href: "/admin/videos", label: "Video Management", icon: Film },
  { href: "/admin/model-logs", label: "AI Logs", icon: FileText },
  { href: "/admin/render-logs", label: "Render Logs", icon: Activity },
];

export function NavSidebar() {
  const pathname = usePathname();
  const { logout, email, role } = useAuth();
  const isAdmin = role === "ADMIN";

  function isActive(href: string) {
    if (href === "/dashboard") return pathname === "/dashboard";
    if (href === "/admin") return pathname === "/admin";
    return pathname.startsWith(href);
  }

  const navLink = (href: string, label: string, Icon: React.ComponentType<{ className?: string }>) => (
    <Link
      key={href}
      href={href}
      className={cn(
        "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
        isActive(href)
          ? "bg-primary/10 font-medium text-primary"
          : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
      )}
    >
      <Icon className="h-4 w-4" />
      {label}
    </Link>
  );

  return (
    <aside className="fixed left-0 top-0 z-40 h-screen w-60 border-r bg-card">
      <div className="flex h-full flex-col">
        <div className="flex h-14 items-center gap-2 border-b px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
            TK
          </div>
          <span className="font-semibold">AI Video</span>
        </div>

        <nav className="flex-1 space-y-6 overflow-y-auto p-3">
          <div className="space-y-0.5">
            {userNavItems.map((item) => navLink(item.href, item.label, item.icon))}
          </div>

          {isAdmin && (
            <div className="space-y-0.5">
              <p className="px-3 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                Admin
              </p>
              {adminNavItems.map((item) => navLink(item.href, item.label, item.icon))}
            </div>
          )}
        </nav>

        <div className="border-t p-4">
          <div className="flex items-center gap-2">
            <p className="truncate text-xs text-muted-foreground">{email}</p>
            {isAdmin && (
              <span className="shrink-0 rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-medium text-primary">
                ADMIN
              </span>
            )}
          </div>
          <button
            onClick={logout}
            className="mt-2 text-xs text-muted-foreground hover:text-foreground"
          >
            Sign out
          </button>
        </div>
      </div>
    </aside>
  );
}
