# Stack Reference

## Table of Contents
1. [Next.js 16.2+](#nextjs-162)
2. [React 19.2+ and React Compiler 1.0](#react-192)
3. [Tailwind CSS v4.2+](#tailwind-css-v42)
4. [shadcn/ui CLI v4](#shadcnui-cli-v4)
5. [Motion v12+](#motion-v12)
6. [Zustand v5+](#zustand-v5)
7. [TanStack Query v5+](#tanstack-query-v5)
8. [TanStack DB v0.6+](#tanstack-db-v06)
9. [Serwist 9.5+](#serwist-95)
10. [PouchDB 9 + CouchDB 3.5](#pouchdb-9--couchdb-35)
11. [Zod](#zod)
12. [Monorepo — Turborepo + pnpm](#monorepo)

---

## Next.js 16.2+

### Configuration

```ts
// next.config.ts
import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  cacheComponents: true,    // enables "use cache" directive
  reactCompiler: true,       // enables React Compiler 1.0
  // turbopack is default — no flag needed
};

export default nextConfig;
```

### App Router Conventions

- `app/` directory is the root; `pages/` must not coexist
- Layouts (`layout.tsx`) are Server Components by default
- Client components require `"use client"` at the top — keep them as leaf nodes
- Server Actions (`"use server"`) handle mutations — never use API routes for same-origin mutations
- `proxy.ts` replaces `middleware.ts` — runs on Node.js runtime, cannot return response bodies

### Server vs Client Split

```
app/
  (domain)/
    page.tsx              # RSC — fetch data here, pass to client shell
    _components/
      client-shell.tsx    # "use client" — manages local state, interactions
      server-card.tsx     # RSC — pure display, no interactivity
```

**Rule:** Data fetching lives in RSCs. State and event handlers live in Client Components. Never fetch in a Client Component when an RSC can do it.

### Cache Components (`"use cache"`)

Three cache scope variants:

```ts
// In-memory LRU cache (default)
"use cache";
export async function getProducts() {
  cacheLife("hours");
  cacheTag("products");
  return db.products.findMany();
}

// Shared remote cache (Redis/KV)
"use cache: remote";
export async function getGlobalConfig() {
  cacheLife("days");
  cacheTag("config");
  return db.config.get();
}

// Private browser-only cache (can access cookies)
"use cache: private";
export async function getUserPreferences() {
  cacheLife("minutes");
  const userId = (await cookies()).get("userId")?.value;
  return db.preferences.findByUser(userId);
}
```

Invalidation uses `revalidateTag("products")` in Server Actions. `updateTag("tag")` provides read-your-writes semantics for instant UI updates after mutation. `refresh()` refreshes uncached data only.

### Server Actions

```ts
// app/(domain)/actions.ts
"use server";

import { revalidateTag } from "next/cache";
import { z } from "zod";

const CreateTodoSchema = z.object({
  title: z.string().min(1).max(255),
  projectId: z.string().uuid(),
});

export async function createTodo(input: unknown) {
  const parsed = CreateTodoSchema.safeParse(input);
  if (!parsed.success) {
    return { success: false as const, error: parsed.error.flatten() };
  }

  const todo = await db.todos.create({ data: parsed.data });
  revalidateTag("todos");
  return { success: true as const, data: todo };
}
```

### Proxy (replaces Middleware)

```ts
// proxy.ts
import type { NextRequest } from "next/server";

export function proxy(request: NextRequest) {
  // Runs on Node.js runtime (not Edge)
  // Cannot return response bodies — use Route Handlers for that
  const url = request.nextUrl.clone();

  // Auth gate
  if (url.pathname.startsWith("/dashboard") && !request.cookies.get("session")) {
    url.pathname = "/login";
    return Response.redirect(url);
  }

  // Geo-routing
  if (request.geo?.country === "VN") {
    url.pathname = `/vn${url.pathname}`;
    return Response.redirect(url);
  }
}
```

### Route Architecture

- **Route Groups** `(marketing)`, `(dashboard)` — organize without URL impact
- **Parallel Routes** `@modal`, `@sidebar` — independent loading/error states
- **Intercepting Routes** `(.)photo/[id]` — modal overlays on navigation
- **Route Handlers** `route.ts` — for external API consumers, webhooks, third-party integrations
- **Loading UI** `loading.tsx` — streaming Suspense boundary per route segment
- **Error UI** `error.tsx` — error boundary per route segment with recovery

### View Transitions (16.2+)

```tsx
<Link href="/products/1" transitionTypes={["slide-left"]}>
  Product Details
</Link>
```

Combine with CSS `@view-transition` for smooth page transitions.

---

## React 19.2+

### New Hooks and APIs

**`use()` — conditional data/context reading:**
```tsx
function ProductDetails({ productPromise }: { productPromise: Promise<Product> }) {
  if (someCondition) {
    const product = use(productPromise); // suspends until resolved
    return <div>{product.name}</div>;
  }
  return <Fallback />;
}

// Also works for context
function ThemedButton() {
  const theme = use(ThemeContext); // replaces useContext
  return <button style={{ color: theme.primary }}>Click</button>;
}
```

**`useActionState()` — form handling (replaces useFormState):**
```tsx
"use client";
import { useActionState } from "react";
import { createTodo } from "./actions";

function TodoForm() {
  const [state, action, isPending] = useActionState(createTodo, null);

  return (
    <form action={action}>
      <input name="title" />
      {state?.error && <p>{state.error.fieldErrors.title}</p>}
      <button disabled={isPending}>
        {isPending ? "Creating..." : "Create"}
      </button>
    </form>
  );
}
```

**`useOptimistic()` — instant UI feedback:**
```tsx
"use client";
import { useOptimistic } from "react";

function TodoList({ todos }: { todos: Todo[] }) {
  const [optimisticTodos, addOptimistic] = useOptimistic(
    todos,
    (current, newTodo: Todo) => [...current, newTodo]
  );

  async function handleAdd(formData: FormData) {
    const title = formData.get("title") as string;
    addOptimistic({ id: crypto.randomUUID(), title, completed: false });
    await createTodo({ title });
  }

  return (
    <div>
      {optimisticTodos.map((todo) => <TodoItem key={todo.id} todo={todo} />)}
      <form action={handleAdd}>
        <input name="title" />
        <button>Add</button>
      </form>
    </div>
  );
}
```

**`useFormStatus()` — parent form state (from react-dom):**
```tsx
"use client";
import { useFormStatus } from "react-dom";

function SubmitButton() {
  const { pending, data, method, action } = useFormStatus();
  return <button disabled={pending}>{pending ? "Submitting..." : "Submit"}</button>;
}
```

**`useEffectEvent()` — separate event logic from Effect dependencies (19.2):**
```tsx
"use client";
import { useEffect, useEffectEvent } from "react";

function ChatRoom({ roomId, onMessage }: Props) {
  const handleMessage = useEffectEvent((msg: Message) => {
    // onMessage doesn't need to be a dependency
    onMessage(msg);
  });

  useEffect(() => {
    const conn = createConnection(roomId);
    conn.on("message", handleMessage);
    return () => conn.close();
  }, [roomId]); // handleMessage is NOT listed as dependency
}
```

**`<Activity>` — background rendering (19.2):**
```tsx
"use client";
import { Activity } from "react";

function TabContainer({ activeTab }: { activeTab: string }) {
  return (
    <>
      <Activity mode={activeTab === "home" ? "visible" : "hidden"}>
        <HomePage />
      </Activity>
      <Activity mode={activeTab === "settings" ? "visible" : "hidden"}>
        <SettingsPage />
      </Activity>
    </>
  );
}
```

### Ref as Prop (no more forwardRef)

```tsx
// Before (deprecated):
const Input = forwardRef<HTMLInputElement, InputProps>((props, ref) => (
  <input ref={ref} {...props} />
));

// After (React 19):
function Input({ ref, ...props }: InputProps & { ref?: React.Ref<HTMLInputElement> }) {
  return <input ref={ref} {...props} />;
}
```

### Context as Provider

```tsx
// Before (deprecated):
<ThemeContext.Provider value={theme}>
  <App />
</ThemeContext.Provider>

// After (React 19):
<ThemeContext value={theme}>
  <App />
</ThemeContext>
```

### Document Metadata Hoisting

```tsx
// In any component — auto-hoists to <head>
function ProductPage({ product }: { product: Product }) {
  return (
    <>
      <title>{product.name} | My Store</title>
      <meta name="description" content={product.description} />
      <link rel="canonical" href={`/products/${product.slug}`} />
      <div>{/* page content */}</div>
    </>
  );
}
```

### Resource Preloading

```tsx
import { prefetchDNS, preconnect, preload, preinit } from "react-dom";

function App() {
  prefetchDNS("https://api.example.com");
  preconnect("https://cdn.example.com", { crossOrigin: "anonymous" });
  preload("https://cdn.example.com/font.woff2", { as: "font", type: "font/woff2" });
  preinit("https://cdn.example.com/analytics.js", { as: "script" });
  return <main>{/* ... */}</main>;
}
```

### React Compiler 1.0

No code changes needed — the compiler automatically memoizes components and hooks at build time. Remove manual `useMemo`, `useCallback`, and `React.memo` calls. The compiler respects React's rules (pure rendering, no side effects during render) and will skip components that violate them, logging warnings.

Configuration:
```ts
// next.config.ts
const nextConfig = {
  reactCompiler: true, // that's it
};
```

---

## Tailwind CSS v4.2+

### CSS-First Configuration

```css
/* app/globals.css */
@import "tailwindcss";
@import "tw-animate-css";     /* replaces tailwindcss-animate plugin */

@theme inline {
  /* Colors in OKLCH */
  --color-primary: oklch(0.6 0.2 260);
  --color-primary-foreground: oklch(0.98 0.005 260);
  --color-secondary: oklch(0.75 0.1 200);
  --color-destructive: oklch(0.55 0.22 27);
  --color-muted: oklch(0.92 0.01 260);
  --color-accent: oklch(0.85 0.05 260);
  --color-background: oklch(0.99 0.002 260);
  --color-foreground: oklch(0.15 0.02 260);
  --color-border: oklch(0.88 0.01 260);
  --color-ring: oklch(0.6 0.2 260);

  /* Typography */
  --font-sans: "Inter", ui-sans-serif, system-ui, sans-serif;
  --font-mono: "JetBrains Mono", ui-monospace, monospace;

  /* Spacing / Radius */
  --radius-lg: 0.75rem;
  --radius-md: 0.5rem;
  --radius-sm: 0.25rem;

  /* Custom breakpoints */
  --breakpoint-3xl: 1920px;
}
```

**PostCSS configuration** (no autoprefixer needed):
```js
// postcss.config.mjs
export default {
  plugins: {
    "@tailwindcss/postcss": {},
  },
};
```

### Custom Variants and Utilities

```css
/* Custom dark mode variant */
@custom-variant dark (&:where(.dark, .dark *));

/* Custom utility */
@utility scrollbar-hidden {
  scrollbar-width: none;
  &::-webkit-scrollbar {
    display: none;
  }
}

/* Custom container query breakpoints */
@custom-variant tablet (@container (min-width: 640px));
```

### Key Utility Renames from v3

| v3 | v4 |
|---|---|
| `shadow-sm` | `shadow-xs` |
| `shadow` | `shadow-sm` |
| `rounded-sm` | `rounded-xs` |
| `rounded` | `rounded-sm` |
| `blur-sm` | `blur-xs` |
| `blur` | `blur-sm` |
| `bg-gradient-to-r` | `bg-linear-to-r` |
| `bg-opacity-50` | `bg-black/50` (modifier) |

### New in v4.1-v4.2

- `text-shadow-*` utilities
- `mask-*` utilities for CSS masking
- `field-sizing-content` for auto-resizing textareas
- 3D transform utilities
- Conic and radial gradient utilities
- Four new color palettes: `mauve`, `olive`, `mist`, `taupe`

---

## shadcn/ui CLI v4

### Initialization

```bash
# New project with defaults
npx shadcn@latest init

# With specific options
npx shadcn@latest init --base radix --style vega

# Scaffold a complete app from template
npx shadcn@latest create
```

### Adding Components

```bash
# From official registry
npx shadcn add button dialog dropdown-menu

# From community registry
npx shadcn add @v0/dashboard

# With inspection flags
npx shadcn add button --dry-run   # preview changes
npx shadcn add button --diff      # show code diff
npx shadcn add button --view      # open in browser
```

### components.json for v4

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "vega",
  "rsc": true,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "app/globals.css",
    "baseColor": "zinc",
    "cssVariables": true,
    "prefix": ""
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "hooks": "@/hooks",
    "ui": "@/components/ui",
    "lib": "@/lib"
  },
  "iconLibrary": "lucide"
}
```

### Available Primitives

- **Radix UI** (default) — stable, widely adopted, full WAI-ARIA
- **Base UI** (MUI team, v1.0) — alternative, same API surface exposed by shadcn/ui

### New Components (2025-2026)

Spinner, Kbd, Button Group, Input Group, Field (universal form field with ARIA), Item (list item display), Empty (empty state), Calendar, Combobox (rebuilt), Native Select, Direction (RTL), Typography.

---

## Motion v12+

### Imports (always use `motion/react`, never `framer-motion`)

```tsx
"use client";
import { motion, AnimatePresence } from "motion/react";
```

For SSR/Next.js client components:
```tsx
"use client";
import { motion } from "motion/react-client";
```

### LazyMotion Code Splitting

```tsx
"use client";
import { LazyMotion, domAnimation, m } from "motion/react";

// Wrap app or feature root — reduces initial bundle from ~34kb to ~4.6kb
function AnimatedFeature() {
  return (
    <LazyMotion features={domAnimation}>
      <m.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -20 }}
      >
        Content
      </m.div>
    </LazyMotion>
  );
}
```

### AnimatePresence for Exit Animations

```tsx
"use client";
import { AnimatePresence, motion } from "motion/react";

function ToastContainer({ toasts }: { toasts: Toast[] }) {
  return (
    <AnimatePresence mode="popLayout">
      {toasts.map((toast) => (
        <motion.div
          key={toast.id}
          initial={{ opacity: 0, x: 100 }}
          animate={{ opacity: 1, x: 0 }}
          exit={{ opacity: 0, x: 100 }}
          layout
        >
          {toast.message}
        </motion.div>
      ))}
    </AnimatePresence>
  );
}
```

### Layout Animations

```tsx
"use client";
import { motion } from "motion/react";

// Shared layout animations via layoutId
function ProductCard({ product, isExpanded }: Props) {
  return (
    <motion.div layoutId={`product-${product.id}`}>
      <motion.img layoutId={`image-${product.id}`} src={product.image} />
      <motion.h2 layoutId={`title-${product.id}`}>{product.name}</motion.h2>
    </motion.div>
  );
}
```

### Gesture System

```tsx
"use client";
import { motion } from "motion/react";

<motion.button
  whileHover={{ scale: 1.05 }}
  whileTap={{ scale: 0.95 }}
  whileFocus={{ boxShadow: "0 0 0 3px oklch(0.6 0.2 260 / 0.5)" }}
  drag="x"
  dragConstraints={{ left: -100, right: 100 }}
>
  Interactive Button
</motion.button>
```

### OKLCH Animation Support (v12)

```tsx
<motion.div
  animate={{ backgroundColor: "oklch(0.6 0.2 260)" }}
  transition={{ duration: 0.5 }}
/>
```

---

## Zustand v5+

### Store Creation (currying syntax for TypeScript)

```ts
import { create } from 'zustand';
import { devtools, persist } from 'zustand/middleware';

interface TodoSlice {
  todos: Todo[];
  filter: 'all' | 'active' | 'completed';
  addTodo: (title: string) => void;
  toggleTodo: (id: string) => void;
  setFilter: (filter: TodoSlice['filter']) => void;
}

export const useTodoStore = create<TodoSlice>()(
  devtools(
    persist(
      (set) => ({
        todos: [],
        filter: 'all',
        addTodo: (title) =>
          set(
            (state) => ({
              todos: [...state.todos, { id: crypto.randomUUID(), title, completed: false }],
            }),
            false,
            'addTodo'
          ),
        toggleTodo: (id) =>
          set(
            (state) => ({
              todos: state.todos.map((t) =>
                t.id === id ? { ...t, completed: !t.completed } : t
              ),
            }),
            false,
            'toggleTodo'
          ),
        setFilter: (filter) => set({ filter }, false, 'setFilter'),
      }),
      { name: 'todo-store' }
    )
  )
);
```

### Using Stores in Components

```tsx
"use client";
import { useTodoStore } from '@/features/todos/store';
import { useShallow } from 'zustand/shallow';

// Atomic selector — single primitive, no re-render issues
function TodoCount() {
  const count = useTodoStore((s) => s.todos.length);
  return <span>{count} todos</span>;
}

// Multiple values — use useShallow to prevent unnecessary re-renders
function TodoControls() {
  const { filter, setFilter, addTodo } = useTodoStore(
    useShallow((s) => ({
      filter: s.filter,
      setFilter: s.setFilter,
      addTodo: s.addTodo,
    }))
  );
  return (/* ... */);
}
```

### Slices Pattern for Large Stores

```ts
import type { StateCreator } from 'zustand';

interface AuthSlice {
  user: User | null;
  login: (credentials: Credentials) => Promise<void>;
  logout: () => void;
}

interface CartSlice {
  items: CartItem[];
  addItem: (product: Product) => void;
  removeItem: (id: string) => void;
}

const createAuthSlice: StateCreator<AuthSlice & CartSlice, [], [], AuthSlice> = (set) => ({
  user: null,
  login: async (credentials) => {
    const user = await api.login(credentials);
    set({ user }, false, 'auth/login');
  },
  logout: () => set({ user: null }, false, 'auth/logout'),
});

const createCartSlice: StateCreator<AuthSlice & CartSlice, [], [], CartSlice> = (set) => ({
  items: [],
  addItem: (product) =>
    set((state) => ({ items: [...state.items, { product, quantity: 1 }] }), false, 'cart/add'),
  removeItem: (id) =>
    set((state) => ({ items: state.items.filter((i) => i.product.id !== id) }), false, 'cart/remove'),
});

export const useAppStore = create<AuthSlice & CartSlice>()(
  devtools((...a) => ({
    ...createAuthSlice(...a),
    ...createCartSlice(...a),
  }))
);
```

### SSR / Next.js App Router Pattern

```tsx
// providers/store-provider.tsx
"use client";
import { createContext, useContext, useRef, type ReactNode } from 'react';
import { createStore, type StoreApi } from 'zustand';

type AppStore = { /* ... */ };

const StoreContext = createContext<StoreApi<AppStore> | null>(null);

export function StoreProvider({ children, initialState }: { children: ReactNode; initialState?: Partial<AppStore> }) {
  const storeRef = useRef<StoreApi<AppStore>>(null);
  if (!storeRef.current) {
    storeRef.current = createStore<AppStore>()((set) => ({
      ...defaultState,
      ...initialState,
    }));
  }
  return <StoreContext value={storeRef.current}>{children}</StoreContext>;
}

export function useAppStore<T>(selector: (state: AppStore) => T): T {
  const store = useContext(StoreContext);
  if (!store) throw new Error('useAppStore must be used within StoreProvider');
  return useStore(store, selector);
}
```

---

## TanStack Query v5+

### Query Options Pattern (recommended)

```ts
// features/products/queries.ts
import { queryOptions } from "@tanstack/react-query";
import { ProductSchema, type Product } from "./schemas";

export const productListOptions = () =>
  queryOptions({
    queryKey: ["products"],
    queryFn: async (): Promise<Product[]> => {
      const res = await fetch("/api/products");
      const data = await res.json();
      return ProductSchema.array().parse(data);
    },
    staleTime: 60_000,
  });

export const productDetailOptions = (id: string) =>
  queryOptions({
    queryKey: ["products", id],
    queryFn: async (): Promise<Product> => {
      const res = await fetch(`/api/products/${id}`);
      const data = await res.json();
      return ProductSchema.parse(data);
    },
    staleTime: 5 * 60_000,
  });
```

### Server Component Prefetching

```tsx
// app/products/page.tsx (Server Component)
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { getQueryClient } from "@/lib/query-client";
import { productListOptions } from "@/features/products/queries";
import { ProductList } from "./_components/product-list";

export default async function ProductsPage() {
  const queryClient = getQueryClient();
  await queryClient.prefetchQuery(productListOptions());

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ProductList />
    </HydrationBoundary>
  );
}
```

### Client Component Usage

```tsx
// app/products/_components/product-list.tsx
"use client";
import { useSuspenseQuery } from "@tanstack/react-query";
import { productListOptions } from "@/features/products/queries";

export function ProductList() {
  // data is always defined (T, never undefined)
  const { data: products } = useSuspenseQuery(productListOptions());

  return (
    <ul>
      {products.map((product) => (
        <li key={product.id}>{product.name}</li>
      ))}
    </ul>
  );
}
```

### Query Client Singleton

```ts
// lib/query-client.ts
import { QueryClient, defaultShouldDehydrateQuery, isServer } from "@tanstack/react-query";

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60_000,
        gcTime: 5 * 60_000,
      },
      dehydrate: {
        shouldDehydrateQuery: (query) =>
          defaultShouldDehydrateQuery(query) || query.state.status === "pending",
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

export function getQueryClient() {
  if (isServer) return makeQueryClient();
  return (browserQueryClient ??= makeQueryClient());
}
```

### Mutations

```tsx
"use client";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function CreateProductForm() {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (data: CreateProductInput) =>
      fetch("/api/products", { method: "POST", body: JSON.stringify(data) }).then((r) => r.json()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["products"] });
    },
  });

  return (/* ... */);
}
```

---

## TanStack DB v0.6+

### Collection Setup

```ts
// features/todos/collections.ts
import { createQueryCollection, createLocalOnlyCollection } from "@tanstack/db";
import { queryOptions } from "@tanstack/react-query";
import { TodoSchema, type Todo } from "./schemas";

export const todoCollection = createQueryCollection<Todo>({
  getKey: (todo) => todo.id,
  getId: (todo) => todo.id,
  schema: TodoSchema,

  // Populated from TanStack Query
  query: queryOptions({
    queryKey: ["todos"],
    queryFn: () => fetch("/api/todos").then((r) => r.json()),
  }),

  // Optimistic mutation handlers
  onInsert: async (todo) => {
    const res = await fetch("/api/todos", {
      method: "POST",
      body: JSON.stringify(todo),
    });
    return res.json();
  },
  onUpdate: async (todo) => {
    const res = await fetch(`/api/todos/${todo.id}`, {
      method: "PATCH",
      body: JSON.stringify(todo),
    });
    return res.json();
  },
  onDelete: async (todo) => {
    await fetch(`/api/todos/${todo.id}`, { method: "DELETE" });
  },
});

// For ephemeral/local-only data
export const draftCollection = createLocalOnlyCollection<Draft>({
  getKey: (draft) => draft.id,
});
```

### Live Queries with Reactive Updates

```tsx
"use client";
import { useLiveQuery } from "@tanstack/react-db";
import { todoCollection, projectCollection } from "./collections";
import { eq } from "@tanstack/db";

function ActiveTodos() {
  const { data: activeTodos } = useLiveQuery((q) =>
    q
      .from({ todo: todoCollection })
      .where(({ todo }) => eq(todo.completed, false))
      .orderBy(({ todo }) => todo.createdAt, "desc")
      .select(({ todo }) => ({
        id: todo.id,
        title: todo.title,
        createdAt: todo.createdAt,
      }))
  );

  return (
    <ul>
      {activeTodos.map((todo) => (
        <li key={todo.id}>{todo.title}</li>
      ))}
    </ul>
  );
}
```

### Joins with Live Queries

```tsx
const { data } = useLiveQuery((q) =>
  q
    .from({ todo: todoCollection })
    .join({ project: projectCollection }, ({ todo, project }) =>
      eq(todo.projectId, project.id)
    )
    .where(({ todo }) => eq(todo.completed, false))
    .select(({ todo, project }) => ({
      id: todo.id,
      title: todo.title,
      projectName: project.name,
    }))
);
```

### Transactional Optimistic Mutations

```tsx
"use client";
import { todoCollection } from "./collections";

function TodoItem({ todo }: { todo: Todo }) {
  async function handleToggle() {
    // Optimistic — UI updates instantly, rolls back on server rejection
    todoCollection.update(todo.id, { completed: !todo.completed });
  }

  async function handleDelete() {
    todoCollection.delete(todo.id);
  }

  return (
    <li>
      <input type="checkbox" checked={todo.completed} onChange={handleToggle} />
      <span>{todo.title}</span>
      <button onClick={handleDelete}>Delete</button>
    </li>
  );
}
```

### Persistence (v0.6)

```ts
import { createQueryCollection } from "@tanstack/db";
import { createSQLitePersistence } from "@tanstack/db-persistence-sqlite";

const persistence = createSQLitePersistence({
  database: "app-db",
});

const todoCollection = createQueryCollection<Todo>({
  getKey: (todo) => todo.id,
  persistence,
  // ... other config
});
```

### Hierarchical Includes (v0.6)

```tsx
const { data: projects } = useLiveQuery((q) =>
  q
    .from({ project: projectCollection })
    .include(({ project }) => ({
      todos: (q2) =>
        q2
          .from({ todo: todoCollection })
          .where(({ todo }) => eq(todo.projectId, project.id)),
    }))
);
// Result: Project[] with nested todos: Todo[]
```

---

## Serwist 9.5+

### Next.js Integration

```ts
// next.config.ts
import withSerwistInit from "@serwist/next";

const withSerwist = withSerwistInit({
  swSrc: "app/sw.ts",
  swDest: "public/sw.js",
});

export default withSerwist({
  cacheComponents: true,
  reactCompiler: true,
});
```

### Service Worker

```ts
// app/sw.ts
import { defaultCache } from "@serwist/next/worker";
import type { PrecacheEntry, SerwistGlobalConfig } from "serwist";
import { Serwist } from "serwist";

declare global {
  interface WorkerGlobalScope extends SerwistGlobalConfig {
    __SW_MANIFEST: (PrecacheEntry | string)[] | undefined;
  }
}

declare const self: ServiceWorkerGlobalScope;

const serwist = new Serwist({
  precacheEntries: self.__SW_MANIFEST,
  skipWaiting: true,
  clientsClaim: true,
  navigationPreload: true,
  runtimeCaching: defaultCache,
  fallbacks: {
    entries: [
      {
        url: "/~offline",
        matcher: ({ request }) => request.destination === "document",
      },
    ],
  },
});

serwist.addEventListeners();
```

### Background Sync for Offline Mutations

```ts
// app/sw.ts (additional config)
import { BackgroundSyncQueue, NetworkOnly } from "serwist";

const bgSyncQueue = new BackgroundSyncQueue("api-mutations", {
  maxRetentionTime: 24 * 60, // 24 hours in minutes
});

serwist.registerCapture(
  ({ url, request }) =>
    url.pathname.startsWith("/api/") && request.method !== "GET",
  new NetworkOnly({
    plugins: [
      {
        fetchDidFail: async ({ request }) => {
          await bgSyncQueue.pushRequest({ request });
        },
      },
    ],
  })
);
```

### PWA Manifest

```ts
// app/manifest.ts
import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "My App",
    short_name: "App",
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#000000",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png" },
    ],
  };
}
```

---

## PouchDB 9 + CouchDB 3.5

### Basic Setup

```ts
import PouchDB from "pouchdb-browser";
import PouchDBFind from "pouchdb-find";

PouchDB.plugin(PouchDBFind);

const localDb = new PouchDB("myapp");
const remoteDb = new PouchDB("https://couch.example.com/myapp");
```

### Bidirectional Sync

```ts
const sync = localDb.sync(remoteDb, {
  live: true,
  retry: true,
  // Only sync specific docs
  filter: "app/by_user",
  query_params: { userId: currentUser.id },
})
  .on("change", (info) => console.log("Sync change:", info))
  .on("paused", () => console.log("Sync paused"))
  .on("active", () => console.log("Sync resumed"))
  .on("error", (err) => console.error("Sync error:", err));

// Cancel on cleanup
sync.cancel();
```

### Queries (always specify limit in v9)

```ts
const result = await localDb.find({
  selector: {
    type: "todo",
    completed: false,
  },
  sort: [{ createdAt: "desc" }],
  limit: 50, // REQUIRED in v9 — default is 25
});
```

### Conflict Resolution

```ts
async function resolveConflict(docId: string) {
  const doc = await localDb.get(docId, { conflicts: true });
  if (!doc._conflicts?.length) return;

  // Get all conflicting revisions
  const conflicts = await Promise.all(
    doc._conflicts.map((rev) => localDb.get(docId, { rev }))
  );

  // Merge strategy: last-write-wins by updatedAt
  const allVersions = [doc, ...conflicts].sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
  );
  const winner = allVersions[0];

  // Remove losing revisions
  const deletions = allVersions.slice(1).map((loser) => ({
    _id: loser._id,
    _rev: loser._rev,
    _deleted: true,
  }));

  await localDb.bulkDocs([winner, ...deletions]);
}
```

---

## Zod

### Schema as Single Source of Truth

```ts
import { z } from "zod";

// Schema is the source of truth for types AND runtime validation
export const TodoSchema = z.object({
  id: z.string().uuid(),
  title: z.string().min(1).max(255),
  completed: z.boolean().default(false),
  projectId: z.string().uuid().optional(),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

// Infer type from schema — never hand-write the interface
export type Todo = z.infer<typeof TodoSchema>;

// Create/update schemas derived from base
export const CreateTodoSchema = TodoSchema.pick({ title: true, projectId: true });
export type CreateTodoInput = z.infer<typeof CreateTodoSchema>;

export const UpdateTodoSchema = TodoSchema.partial().required({ id: true });
export type UpdateTodoInput = z.infer<typeof UpdateTodoSchema>;
```

### Validation at Every Boundary

```ts
// Server Action input
const parsed = CreateTodoSchema.safeParse(input);
if (!parsed.success) return { error: parsed.error.flatten() };

// API response
const products = ProductSchema.array().parse(await res.json());

// Route params
const { id } = z.object({ id: z.string().uuid() }).parse(params);

// Search params
const { page, limit } = z.object({
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(100).default(20),
}).parse(Object.fromEntries(searchParams));
```

---

## Monorepo

Use **Turborepo** with **pnpm workspaces** as the default monorepo setup.

```
apps/
  web/              # Next.js 16 app
  docs/             # (optional) documentation site
packages/
  ui/               # shadcn/ui components, shared design tokens
  db/               # TanStack DB collections, PouchDB sync helpers
  validation/       # Zod schemas shared across apps
  config/           # Shared tsconfig, tailwind base theme
```

**Key rules:**
- `packages/validation` is the single source of truth for Zod schemas — never duplicate
- `packages/ui` exports only presentational components; no business logic
- Every package has its own `package.json` with `exports` field using subpath exports
- Turborepo pipeline: `build` depends on `^build`; `lint` and `type-check` run in parallel

**tsconfig strategy:**
```jsonc
// packages/config/tsconfig.base.json
{
  "compilerOptions": {
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "moduleResolution": "bundler",
    "paths": { "@/*": ["./src/*"] }
  }
}
```