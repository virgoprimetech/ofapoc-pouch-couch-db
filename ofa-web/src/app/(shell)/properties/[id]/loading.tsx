export default function PropertyLoading() {
  return (
    <div className="p-6 animate-pulse">
      <div className="mb-6 flex items-center gap-2">
        <div className="h-8 w-8 rounded-sm bg-muted" />
        <div className="h-4 w-24 bg-muted rounded" />
      </div>
      <div className="space-y-4 max-w-3xl">
        <div className="rounded-sm border p-6 space-y-4">
          <div className="flex items-start justify-between">
            <div className="flex items-start gap-4">
              <div className="size-12 rounded-sm bg-muted" />
              <div className="space-y-2">
                <div className="h-5 w-48 bg-muted rounded" />
                <div className="h-4 w-32 bg-muted rounded" />
              </div>
            </div>
            <div className="h-6 w-16 bg-muted rounded" />
          </div>
          <div className="h-px bg-muted" />
          <div className="grid grid-cols-2 gap-4">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="space-y-1.5">
                <div className="h-3 w-16 bg-muted rounded" />
                <div className="h-4 w-32 bg-muted rounded" />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
