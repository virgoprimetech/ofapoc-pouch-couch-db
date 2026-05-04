"use client";

import type { Toast } from "@/hooks/use-toast";

export function ToastContainer({ toasts }: { toasts: Toast[] }) {
  if (toasts.length === 0) return null;
  return (
    <div aria-live="polite" className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((t) => (
        <div
          key={t.id}
          role="status"
          className={[
            "flex items-center gap-2 rounded-sm px-4 py-2.5 text-sm font-medium shadow-md",
            "animate-[slide-in_200ms_ease-out]",
            t.variant === "success" && "bg-primary text-primary-foreground",
            t.variant === "error" && "bg-destructive text-white",
            t.variant === "info" && "bg-secondary text-secondary-foreground",
          ]
            .filter(Boolean)
            .join(" ")}
        >
          <span className="size-1.5 shrink-0 rounded-full bg-current" />
          {t.message}
        </div>
      ))}
    </div>
  );
}
