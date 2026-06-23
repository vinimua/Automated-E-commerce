"use client";

import { useAuth } from "@/hooks/use-auth";
import { cn } from "@/lib/utils";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { LayoutDashboard, Package, Video, PlusCircle, BarChart3 } from "lucide-react";

const navItems = [
  { href: "/dashboard", label: "工作台", icon: LayoutDashboard },
  { href: "/products/new", label: "新建视频", icon: PlusCircle },
  { href: "/dashboard", label: "商品管理", icon: Package },  // reuses dashboard for now
  { href: "/dashboard", label: "视频库", icon: Video },       // Phase 6
];

export function NavSidebar() {
  const pathname = usePathname();
  const { logout, email } = useAuth();

  return (
    <aside className="fixed left-0 top-0 z-40 h-screen w-60 border-r bg-card">
      <div className="flex h-full flex-col">
        {/* Logo */}
        <div className="flex h-14 items-center gap-2 border-b px-6">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-primary-foreground">
            TK
          </div>
          <span className="font-semibold">AI Video</span>
        </div>

        {/* Nav */}
        <nav className="flex-1 space-y-0.5 p-3">
          {navItems.map((item) => {
            const isActive = pathname === item.href;
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors",
                  isActive
                    ? "bg-primary/10 text-primary font-medium"
                    : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
                )}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
        </nav>

        {/* User */}
        <div className="border-t p-4">
          <p className="truncate text-xs text-muted-foreground">{email}</p>
          <button
            onClick={logout}
            className="mt-2 text-xs text-muted-foreground hover:text-foreground"
          >
            退出登录
          </button>
        </div>
      </div>
    </aside>
  );
}
