import { createSerwistRoute } from "@serwist/turbopack";
import type { NextRequest } from "next/server";

// Build-time revision for cache busting on redeployment
const revision = `v-${Date.now()}`;

const serwistRoute = createSerwistRoute({
  swSrc: "src/sw.ts",
  useNativeEsbuild: true,
  additionalPrecacheEntries: [
    { url: "/", revision },
    { url: "/manifest.webmanifest", revision },
  ],
});

// Static values required by Turbopack's compile-time analysis
export const dynamic = "force-static";
export const dynamicParams = false;
export const revalidate = false;

export async function generateStaticParams() {
  const params = await serwistRoute.generateStaticParams();
  return params.map((p) => ({ path: [p.path] }));
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ path: string[] }> },
) {
  const { path: pathArr } = await context.params;
  const adaptedContext = {
    params: Promise.resolve({ path: pathArr.join("/") }),
  };
  return serwistRoute.GET(
    request,
    adaptedContext as Parameters<typeof serwistRoute.GET>[1],
  );
}
