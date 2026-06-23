"use client";

import { Inter } from "next/font/google";
import { Toaster } from "sonner";
import { AuthProvider } from "@/hooks/use-auth";
import { AuthGuard } from "@/components/auth-guard";
import { NavSidebar } from "@/components/nav-sidebar";
import { useAuth } from "@/hooks/use-auth";
import { usePathname } from "next/navigation";
import "./globals.css";

const inter = Inter({ subsets: ["latin"] });

const PUBLIC_PATHS = ["/login", "/register"];

function AppShell({ children }: { children: React.ReactNode }) {
  const { isLoggedIn } = useAuth();
  const pathname = usePathname();
  const isPublic = PUBLIC_PATHS.some((p) => pathname.startsWith(p));

  if (isPublic || !isLoggedIn) {
    return <main className="min-h-screen bg-background antialiased">{children}</main>;
  }

  return (
    <div className="min-h-screen bg-background antialiased">
      <NavSidebar />
      <main className="pl-60">{children}</main>
    </div>
  );
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={inter.className}>
        <AuthProvider>
          <AuthGuard>
            <AppShell>{children}</AppShell>
          </AuthGuard>
          <Toaster position="top-right" richColors />
        </AuthProvider>
      </body>
    </html>
  );
}
