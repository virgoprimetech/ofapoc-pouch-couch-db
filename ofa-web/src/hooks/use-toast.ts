"use client";

import { useState, useCallback } from "react";

export type Toast = { id: string; message: string; variant: "success" | "error" | "info" };

export function useToast() {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const addToast = useCallback((message: string, variant: Toast["variant"] = "info") => {
    const id = crypto.randomUUID();
    setToasts((prev) => [...prev, { id, message, variant }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4000);
  }, []);
  return { toasts, addToast };
}
