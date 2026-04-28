"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import {
  LoginSchema,
  LoginResponseSchema,
  RefreshResponseSchema,
  type AuthUser,
} from "./schemas";
import { parseTokenClaims } from "./token";

const GW_URL = (process.env.OFA_GW_URL ?? "http://localhost:8080") + "/identity/identity";

interface IdentityErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: Record<string, string> | null;
}

interface LoginResult {
  success: true;
  user: AuthUser;
  accessToken: string;
}

interface LoginError {
  success: false;
  error: string;
  status: number;
}

interface RefreshSuccess {
  success: true;
  accessToken: string;
}

interface RefreshFailure {
  success: false;
  error: string;
  status: number;
  terminal: boolean;
}

interface AuthCookiesInput {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export type LoginActionResult = LoginResult | LoginError;
export type RefreshActionResult = RefreshSuccess | RefreshFailure;

function parseErrorMessage(status: number, body: unknown): string {
  if (status === 401) {
    return "Session expired. Please sign in again.";
  }

  if (status === 403) {
    return "Session refresh was denied. Please sign in again.";
  }

  if (
    typeof body === "object" &&
    body !== null &&
    "message" in body &&
    typeof (body as Record<string, unknown>).message === "string"
  ) {
    const error = body as IdentityErrorResponse;

    if (error.details && Object.keys(error.details).length > 0) {
      const fieldErrors = Object.entries(error.details)
        .map(([field, msg]) => `${field}: ${msg}`)
        .join("; ");
      return `${error.message} — ${fieldErrors}`;
    }

    return error.message;
  }

  const statusMessages: Record<number, string> = {
    400: "Invalid request. Please check your input.",
    401: "Invalid credentials.",
    403: "Access denied.",
    404: "Account not found.",
    429: "Too many attempts. Please try again later.",
    500: "Server error. Please try again later.",
    502: "Service unavailable. Please try again later.",
    503: "Service unavailable. Please try again later.",
  };

  return statusMessages[status] ?? "Authentication failed. Please try again.";
}

async function setAuthCookies({ accessToken, refreshToken, expiresIn }: AuthCookiesInput) {
  const cookieStore = await cookies();
  const secure = process.env.NODE_ENV === "production";

  cookieStore.set("ofa_access_token", accessToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    maxAge: expiresIn,
  });

  cookieStore.set("ofa_refresh_token", refreshToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    maxAge: 7 * 24 * 60 * 60,
  });
}

async function clearAuthCookies() {
  const cookieStore = await cookies();
  cookieStore.delete("ofa_access_token");
  cookieStore.delete("ofa_refresh_token");
}

async function clearAccessTokenCookie() {
  const cookieStore = await cookies();
  cookieStore.delete("ofa_access_token");
}

export async function loginAction(input: unknown): Promise<LoginActionResult> {
  const parsed = LoginSchema.safeParse(input);
  if (!parsed.success) {
    const firstError = parsed.error.issues[0];
    return {
      success: false,
      error: firstError?.message ?? "Invalid input",
      status: 400,
    };
  }

  const { tenant, username, password } = parsed.data;

  let response: Response;
  try {
    response = await fetch(`${GW_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ tenant, username, password }),
    });
  } catch {
    return {
      success: false,
      error: "Unable to reach authentication service. Please check your connection.",
      status: 503,
    };
  }

  if (!response.ok) {
    let errorBody: unknown;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = null;
    }

    return {
      success: false,
      error: parseErrorMessage(response.status, errorBody),
      status: response.status,
    };
  }

  const body: unknown = await response.json();
  console.log("Login response:", body);
  const result = LoginResponseSchema.safeParse(body);
  console.log("Login result:", result);
  if (!result.success) {
    return {
      success: false,
      error: "Unexpected response from server.",
      status: 502,
    };
  }

  const { accessToken, refreshToken, expiresIn, ...userFields } = result.data;
  await setAuthCookies({ accessToken, refreshToken, expiresIn });

  const claims = parseTokenClaims(accessToken);
  if (!claims) {
    await clearAuthCookies();
    return {
      success: false,
      error: "Unexpected response from server.",
      status: 502,
    };
  }

  const user: AuthUser = {
    userId: userFields.userId,
    username: userFields.username,
    email: userFields.email,
    tenantId: userFields.tenantId,
    tenantCode: claims.tenant_code,
    roles: userFields.roles,
  };

  return { success: true, user, accessToken };
}

export async function refreshAccessTokenAction(): Promise<RefreshActionResult> {
  const cookieStore = await cookies();
  const refreshToken = cookieStore.get("ofa_refresh_token")?.value;

  await clearAccessTokenCookie();

  if (!refreshToken) {
    await clearAuthCookies();
    return {
      success: false,
      error: "Your session has expired. Please sign in again.",
      status: 401,
      terminal: true,
    };
  }

  let response: Response;
  try {
    response = await fetch(`${GW_URL}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    return {
      success: false,
      error: "Unable to refresh session right now. Please try again shortly.",
      status: 503,
      terminal: false,
    };
  }

  if (!response.ok) {
    let errorBody: unknown;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = null;
    }

    const terminal = response.status === 401 || response.status === 403;
    if (terminal) {
      await clearAuthCookies();
    }

    return {
      success: false,
      error: parseErrorMessage(response.status, errorBody),
      status: response.status,
      terminal,
    };
  }

  const body: unknown = await response.json();
  const result = RefreshResponseSchema.safeParse(body);
  if (!result.success) {
    await clearAuthCookies();
    return {
      success: false,
      error: "Unexpected response from server.",
      status: 502,
      terminal: true,
    };
  }

  const { accessToken, refreshToken: nextRefreshToken, expiresIn } = result.data;
  await setAuthCookies({
    accessToken,
    refreshToken: nextRefreshToken,
    expiresIn,
  });

  return {
    success: true,
    accessToken,
  };
}

export async function logoutAction(): Promise<void> {
  await clearAuthCookies();
  redirect("/login");
}

export async function getAccessTokenAction(): Promise<string | null> {
  const cookieStore = await cookies();
  return cookieStore.get("ofa_access_token")?.value ?? null;
}
