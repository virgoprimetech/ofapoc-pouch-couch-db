"use client";

import { useState } from "react";
import { destroyDb } from "@/lib/db/index";
import { stopSync } from "@/lib/db/sync";
import { useAuthStore } from "@/features/auth/store";
import { logoutAction } from "@/features/auth/actions";
import { useRouter } from "next/navigation";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { RiLoginBoxLine, RiLogoutBoxRLine, RiUserLine } from "@remixicon/react";
import { Button } from "@/components/ui/button";

export function UserMenu() {
  const user = useAuthStore((s) => s.user);
  const hydrated = useAuthStore((s) => s.hydrated);
  const clear = useAuthStore((s) => s.clear);
  const router = useRouter();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function performLocalLogoutCleanup() {
    await stopSync();
    await destroyDb();
  }

  // Wait for localStorage rehydration before deciding what to render
  if (!hydrated) {
    return (
      <Button variant="ghost" className="gap-2" disabled aria-busy="true">
        <div className="flex size-6 items-center justify-center rounded-sm bg-muted" />
      </Button>
    );
  }

  const isAnonymous = !user;

  const displayName = isAnonymous ? "Anonymous" : user.username;
  const initials = displayName.slice(0, 2).toUpperCase();

  async function handleLogout() {
    if (isLoggingOut) return;

    setIsLoggingOut(true);

    try {
      await performLocalLogoutCleanup();
      clear();
      await logoutAction();
      // logoutAction redirects to /login, but push as fallback
      router.push("/login");
    } finally {
      setIsLoggingOut(false);
    }
  }

  async function handleLogin() {
    router.push("/login");
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className="gap-2 cursor-pointer"
        >
          <div className={`flex size-6 items-center justify-center rounded-sm text-xs font-medium ${isAnonymous ? "bg-muted text-muted-foreground" : "bg-primary text-primary-foreground"}`}>
            {initials}
          </div>
          <span className="hidden sm:inline text-sm">{displayName}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        {isAnonymous ? (
          <>
            <DropdownMenuLabel className="flex flex-col gap-0.5">
              <span className="font-medium">Anonymous</span>
              <span className="text-xs text-muted-foreground font-normal">Not signed in</span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleLogin} className="cursor-pointer">
              <RiLoginBoxLine className="size-4" />
              Sign in
            </DropdownMenuItem>
          </>
        ) : (
          <>
            <DropdownMenuLabel className="flex flex-col gap-0.5">
              <span className="font-medium">{user.username}</span>
              <span className="text-xs text-muted-foreground font-normal">{user.email}</span>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuLabel className="text-xs text-muted-foreground">
              <div className="flex items-center gap-1.5">
                <RiUserLine className="size-3.5" />
                {user.roles.join(", ")}
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              variant="destructive"
              onClick={handleLogout}
              disabled={isLoggingOut}
              className="cursor-pointer"
            >
              <RiLogoutBoxRLine className="size-4" />
              Sign out
            </DropdownMenuItem>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}