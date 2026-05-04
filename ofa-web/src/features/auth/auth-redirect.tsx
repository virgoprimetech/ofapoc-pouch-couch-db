"use client";

import { useAuthStore } from "./store";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

/**
 * Redirects to /login when the auth store has hydrated and no user is present.
 * Place as a sibling of {children} inside the shell layout — renders no UI,
 * only calls router.push("/login") as a side effect.
 */
export function AuthRedirect() {
  const user = useAuthStore((s) => s.user);
  const hydrated = useAuthStore((s) => s.hydrated);
  const router = useRouter();

  useEffect(() => {
    if (hydrated && !user) {
      router.push("/login");
    }
  }, [hydrated, user, router]);

  return null;
}