import { z } from "zod";

// Matches identity service LoginRequest
export const LoginSchema = z.object({
  tenant: z
    .string()
    .min(2, "Tenant code must be at least 2 characters")
    .max(50, "Tenant code must be at most 50 characters")
    .regex(
      /^[a-z][a-z0-9_-]*$/,
      "Tenant code must start with a letter and contain only lowercase letters, numbers, underscores, and hyphens"
    ),
  username: z
    .string()
    .min(3, "Username must be at least 3 characters")
    .max(50, "Username must be at most 50 characters")
    .regex(
      /^[a-zA-Z0-9_-]+$/,
      "Username can only contain letters, numbers, underscores, and hyphens"
    ),
  password: z.string().min(1, "Password is required"),
});

export type LoginInput = z.infer<typeof LoginSchema>;

// Matches identity service LoginResponse — returned flat, no envelope
export const LoginResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number(),
  tenantId: z.string().uuid(),
  userId: z.string().uuid(),
  username: z.string(),
  email: z.string().email(),
  roles: z.array(z.string()),
});

export type LoginResponse = z.infer<typeof LoginResponseSchema>;

// Matches identity service RefreshResponse — username is not returned
export const RefreshResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number(),
  tenantId: z.string().uuid(),
  tenantCode: z.string().min(1),
  userId: z.string().uuid(),
  email: z.string().email(),
  roles: z.array(z.string()),
});

export type RefreshResponse = z.infer<typeof RefreshResponseSchema>;

// Client-visible user data — no tokens
export const AuthUserSchema = z.object({
  userId: z.string().uuid(),
  username: z.string(),
  email: z.string().email(),
  tenantId: z.string().uuid(),
  tenantCode: z.string().min(1),
  roles: z.array(z.string()),
});

export type AuthUser = z.infer<typeof AuthUserSchema>;