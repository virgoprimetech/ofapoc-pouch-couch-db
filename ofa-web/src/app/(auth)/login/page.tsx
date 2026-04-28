import type { Metadata } from "next";
import { LoginForm } from "./login-form";

export const metadata: Metadata = {
  title: "Sign In — OFA",
  description: "Sign in to your OFA Property Management account",
};

export default function LoginPage() {
  return <LoginForm />;
}