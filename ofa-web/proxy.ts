import type { NextRequest } from "next/server";

const PUBLIC_PATHS = ["/_next", "/sw", "/manifest.webmanifest", "/favicon.ico"];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const token = request.cookies.get("ofa_access_token");

  if (token){
    request.headers.set("Authorization", `Bearer ${token.value}`);
  }

  // Static assets and internal paths — always allow
  if (PUBLIC_PATHS.some((p) => pathname.startsWith(p) || pathname === p)) {
    return;
  }

  // File extensions (images, fonts, etc.) — no auth needed
  if (pathname.includes(".") && !pathname.endsWith(".html")) {
    return;
  }

  // Authenticated user trying to access /login — redirect to home
  if (pathname === "/login" && token) {
    const homeUrl = request.nextUrl.clone();
    homeUrl.pathname = "/";
    return Response.redirect(homeUrl);
  }

  // Unauthenticated user accessing protected path — redirect to login
  if (pathname !== "/login" && !token) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = "/login";
    return Response.redirect(loginUrl);
  }
}