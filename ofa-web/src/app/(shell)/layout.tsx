import { ClientShell } from "@/components/layout/client-shell";

export default function ShellLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return <ClientShell>{children}</ClientShell>;
}