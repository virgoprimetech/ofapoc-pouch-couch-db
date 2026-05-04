"use client";

import { refreshAccessTokenAction } from "./actions";
import { useAuthStore } from "./store";

export type RefreshTokenResult =
  | { success: true; accessToken: string }
  | { success: false; terminal: boolean; error: string; status: number };

let inFlightRefresh: Promise<RefreshTokenResult> | null = null;

async function runRefresh(): Promise<RefreshTokenResult> {
  const { setAccessToken, clear } = useAuthStore.getState();
  setAccessToken(null);

  const result = await refreshAccessTokenAction();
  if (result.success) {
    setAccessToken(result.accessToken);
    return result;
  }

  if (result.terminal) {
    clear();
  }

  return result;
}

export async function refreshAccessTokenClient(): Promise<RefreshTokenResult> {
  if (!inFlightRefresh) {
    inFlightRefresh = runRefresh().finally(() => {
      inFlightRefresh = null;
    });
  }

  return inFlightRefresh;
}
