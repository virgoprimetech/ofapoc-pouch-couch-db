export interface TokenClaims {
  sub: string;
  username: string;
  tenant_id: string;
  tenant_code: string;
}

function decodeBase64Url(value: string): string {
  const normalized = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
  return atob(padded);
}

export function parseTokenClaims(token: string | null): TokenClaims | null {
  if (!token) {
    return null;
  }

  const [, payload] = token.split(".");
  if (!payload) {
    return null;
  }

  try {
    const decoded = JSON.parse(decodeBase64Url(payload)) as Partial<TokenClaims>;
    if (
      typeof decoded.sub !== "string" ||
      typeof decoded.username !== "string" ||
      typeof decoded.tenant_id !== "string" ||
      typeof decoded.tenant_code !== "string"
    ) {
      return null;
    }

    return {
      sub: decoded.sub,
      username: decoded.username,
      tenant_id: decoded.tenant_id,
      tenant_code: decoded.tenant_code,
    };
  } catch {
    return null;
  }
}
