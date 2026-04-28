export default function AuthLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="min-h-dvh flex items-center justify-center bg-background p-4">
      {children}
    </div>
  );
}