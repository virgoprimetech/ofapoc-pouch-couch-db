import { defaultCache } from "@serwist/turbopack/worker";
import type { PrecacheEntry, SerwistGlobalConfig } from "serwist";
import { ExpirationPlugin, NetworkFirst, Serwist } from "serwist";

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope;

let serwist: Serwist;

const navigationHandler = new NetworkFirst({
  cacheName: "navigation",
  plugins: [
    new ExpirationPlugin({ maxEntries: 64, maxAgeSeconds: 24 * 60 * 60 }),
    {
      handlerDidError: async () => {
        return serwist.matchPrecache("/") ?? Response.error();
      },
    },
  ],
});

serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  skipWaiting: true,
  clientsClaim: true,
  // Navigation handler MUST be first — defaultCache has a same-origin catch-all
  // that would swallow navigation requests before a separately registered route.
  runtimeCaching: [
    {
      matcher: ({ request }) => request.mode === "navigate",
      handler: navigationHandler,
    },
    ...defaultCache,
  ],
  navigationPreload: false,
});

// Suppress uncaught errors from routes that fail offline (e.g. RSC prefetch).
// RSC requests need the server — they can't work offline. Return a proper
// error response instead of letting Serwist throw an uncaught promise rejection.
serwist.setCatchHandler(async ({ request, url }) => {
  if (request.mode === "navigate") {
    const cached = await serwist.matchPrecache("/");
    if (cached) return cached;
  }
  // RSC requests need a network error (not a valid HTTP response) so Next.js
  // falls back to browser navigation, which the navigation handler above serves
  // from the precached app shell.
  if (url.searchParams.has("_rsc") || request.headers.get("RSC") === "1") {
    return Response.error();
  }
  // Serve precached manifest offline
  if (url.pathname === "/manifest.webmanifest") {
    const cached = await serwist.matchPrecache("/manifest.webmanifest");
    if (cached) return cached;
  }
  return new Response("Offline", { status: 503, statusText: "Service Unavailable" });
});

serwist.addEventListeners();
