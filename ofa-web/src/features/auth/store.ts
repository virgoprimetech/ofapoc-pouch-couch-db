import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthUser } from "./schemas";

interface AuthSlice {
  user: AuthUser | null;
  accessToken: string | null;
  hydrated: boolean;
  setUser: (user: AuthUser, accessToken: string) => void;
  setAccessToken: (token: string | null) => void;
  clear: () => void;
}

export const useAuthStore = create<AuthSlice>()(
  persist(
    (set) => ({
      user: null,
      // In-memory only — never persisted to localStorage
      accessToken: null,
      hydrated: false,
      setUser: (user, accessToken) => set({ user, accessToken, hydrated: true }),
      setAccessToken: (token) => set({ accessToken: token }),
      clear: () => set({ user: null, accessToken: null, hydrated: true }),
    }),
    {
      name: "ofa-auth",
      // Only persist user metadata — accessToken stays in-memory
      partialize: (state) => ({ user: state.user }),
      onRehydrateStorage: () => (state) => {
        if (state) state.hydrated = true;
      },
    },
  ),
);