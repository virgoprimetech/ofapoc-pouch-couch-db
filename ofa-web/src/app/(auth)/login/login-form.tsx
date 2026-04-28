"use client";

import { useActionState, useState } from "react";
import { useRouter } from "next/navigation";
import { RiBuilding2Line, RiEyeLine, RiEyeOffLine, RiLoader4Line } from "@remixicon/react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { loginAction, type LoginActionResult } from "@/features/auth/actions";
import { useAuthStore } from "@/features/auth/store";

function SubmitButton({ pending }: { pending: boolean }) {
  return (
    <Button type="submit" disabled={pending} className="w-full gap-1.5 cursor-pointer">
      {pending ? (
        <>
          <RiLoader4Line className="size-4 animate-spin" />
          Signing in...
        </>
      ) : (
        "Sign in"
      )}
    </Button>
  );
}

function PasswordInput({ pending }: { pending: boolean }) {
  const [visible, setVisible] = useState(false);

  return (
    <div className="grid gap-1.5">
      <Label htmlFor="password">Password</Label>
      <div className="relative">
        <Input
          id="password"
          name="password"
          type={visible ? "text" : "password"}
          placeholder="Enter your password"
          autoComplete="current-password"
          required
          disabled={pending}
          className="pr-9"
        />
        <button
          type="button"
          onClick={() => setVisible(!visible)}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
          tabIndex={-1}
          aria-label={visible ? "Hide password" : "Show password"}
        >
          {visible ? (
            <RiEyeOffLine className="size-4" />
          ) : (
            <RiEyeLine className="size-4" />
          )}
        </button>
      </div>
    </div>
  );
}

export function LoginForm() {
  const router = useRouter();
  const setUser = useAuthStore((s) => s.setUser);

  async function handleSubmit(
    _prevState: LoginActionResult | null,
    formData: FormData,
  ): Promise<LoginActionResult | null> {
    const result = await loginAction({
      tenant: (formData.get("tenant") as string) ?? "",
      username: (formData.get("username") as string) ?? "",
      password: (formData.get("password") as string) ?? "",
    });

    if (result.success) {
      setUser(result.user, result.accessToken);
      router.push("/");
      return null;
    }

    return result;
  }

  const [state, action, pending] = useActionState(handleSubmit, null);

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <div className="mx-auto mb-2 flex size-10 items-center justify-center rounded-sm bg-primary text-primary-foreground">
          <RiBuilding2Line className="size-5" />
        </div>
        <CardTitle className="text-lg">Sign in to OFA</CardTitle>
        <CardDescription>
          Enter your credentials to access Property Management
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form action={action} className="grid gap-3">
          {state && !state.success && (
            <div
              className="rounded-sm border border-destructive/20 bg-destructive/10 px-3 py-2 text-xs text-destructive"
              role="alert"
            >
              {state.error}
            </div>
          )}

          <div className="grid gap-1.5">
            <Label htmlFor="tenant">Tenant</Label>
            <Input
              id="tenant"
              name="tenant"
              type="text"
              placeholder="e.g. primetech"
              autoComplete="organization"
              required
              disabled={pending}
            />
          </div>

          <div className="grid gap-1.5">
            <Label htmlFor="username">Username</Label>
            <Input
              id="username"
              name="username"
              type="text"
              placeholder="Enter your username"
              autoComplete="username"
              required
              disabled={pending}
            />
          </div>

          <PasswordInput pending={pending} />

          <SubmitButton pending={pending} />
        </form>
      </CardContent>
    </Card>
  );
}