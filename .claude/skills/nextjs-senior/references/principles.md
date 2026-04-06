# Principles Reference

## Table of Contents
1. [SOLID Principles in Next.js / React / TypeScript](#solid-principles)
2. [Clean Code in Next.js](#clean-code)
3. [Code Smells in Next.js / React](#code-smells)
4. [Design Patterns Mapped to Next.js / TypeScript](#design-patterns)
5. [Additional Principles](#additional-principles)

---

## SOLID Principles

### S — Single Responsibility Principle

A module should have exactly one reason to change.

**In Next.js context:**
- A React component renders UI — it does not fetch data, validate input, or manage business logic
- A Server Action handles one mutation — not multiple unrelated side effects
- A Zustand slice manages one domain's ephemeral state — not the entire app
- A TanStack DB collection represents one entity type — not a grab-bag
- A Zod schema defines one data shape — derive variants with `.pick()`, `.partial()`, `.extend()`

**Violation:**
```tsx
// BAD: Component does rendering, validation, fetching, and business logic
function TodoForm() {
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");

  async function handleSubmit() {
    if (title.length < 1) { setError("Required"); return; } // validation
    if (title.length > 255) { setError("Too long"); return; } // validation
    const res = await fetch("/api/todos", { method: "POST", body: JSON.stringify({ title }) }); // fetching
    if (!res.ok) { setError("Failed"); return; } // error handling
    const todo = await res.json(); // parsing
    // update local state, redirect, etc.
  }
  // ...
}
```

**Fix:** Extract validation to Zod schema, mutation to Server Action, business logic to service, component renders only.

### O — Open/Closed Principle

Open for extension, closed for modification.

**In Next.js context:**
- Use the **Strategy pattern** for pluggable behaviors (cache strategies, sync engines, conflict resolvers)
- Use **composition** (children, render props, slots) instead of modifying base components
- shadcn/ui components are copy-paste — extend by wrapping, not by modifying the source
- TanStack DB collection types are chosen at configuration time, not runtime branching

**Example:**
```tsx
// Open for extension via composition
interface CardProps {
  children: React.ReactNode;
  header?: React.ReactNode;
  footer?: React.ReactNode;
}

function Card({ children, header, footer }: CardProps) {
  return (
    <div className="rounded-lg border bg-card">
      {header && <div className="border-b p-4">{header}</div>}
      <div className="p-4">{children}</div>
      {footer && <div className="border-t p-4">{footer}</div>}
    </div>
  );
}
```

### L — Liskov Substitution Principle

Subtypes must be substitutable for their base types without breaking correctness.

**In Next.js context:**
- Custom hooks that wrap `useSuspenseQuery` must return the same shape — don't add fields that break the contract
- Zustand slices that implement an interface must fulfill the entire interface
- Zod schema refinements must not narrow the type in ways that break consumers
- TanStack DB collection types must satisfy the `Collection<T>` interface regardless of backend

### I — Interface Segregation Principle

Don't force consumers to depend on interfaces they don't use.

**In Next.js context:**
- Component props should be minimal — pass only what the component renders
- Don't pass entire objects when a component needs one field
- Zustand selectors should be atomic — select one value or use `useShallow` for a minimal subset
- TanStack DB live queries should `select()` only the fields the component needs

**Violation:**
```tsx
// BAD: Component receives entire user object but only needs name
function Greeting({ user }: { user: User }) {
  return <h1>Hello, {user.name}</h1>;
}

// GOOD: Accept only what's needed
function Greeting({ name }: { name: string }) {
  return <h1>Hello, {name}</h1>;
}
```

### D — Dependency Inversion Principle

Depend on abstractions, not concretions.

**In Next.js context:**
- Services accept interfaces, not concrete implementations
- Sync engine choice is a configuration decision, not hardcoded in business logic
- Use Zod schemas as the boundary contract — both server and client validate against the same schema
- TanStack DB collections abstract away the data source (REST, ElectricSQL, PowerSync, local)

**Example:**
```ts
// Abstract sync interface
interface SyncEngine<T> {
  subscribe(onChange: (items: T[]) => void): () => void;
  push(item: T): Promise<void>;
}

// Concrete implementations
class PouchDBSync<T> implements SyncEngine<T> { /* ... */ }
class ElectricSync<T> implements SyncEngine<T> { /* ... */ }

// Service depends on abstraction
class TodoService {
  constructor(private sync: SyncEngine<Todo>) {}
  async addTodo(todo: Todo) { await this.sync.push(todo); }
}
```

---

## Clean Code

### Naming

- **Components**: PascalCase, noun-based — `ProductCard`, `TodoList`, `UserAvatar`
- **Hooks**: `use` prefix, verb-based — `useOnlineStatus`, `useTodoActions`, `useProductSearch`
- **Server Actions**: verb-noun — `createTodo`, `updateProduct`, `deleteUser`
- **Schemas**: PascalCase + `Schema` suffix — `TodoSchema`, `CreateTodoSchema`
- **Collections**: camelCase + `Collection` suffix — `todoCollection`, `productCollection`
- **Query options**: camelCase + `Options` suffix — `todoListOptions`, `productDetailOptions`
- **Files**: kebab-case — `todo-list.tsx`, `create-todo.ts`, `todo-schema.ts`
- **Boolean variables**: `is`/`has`/`should` prefix — `isLoading`, `hasError`, `shouldSync`

### Function Design

**Keep functions small** — 5-20 lines preferred, never over 50.

**Guard clauses first** — handle error cases early, keep the happy path unindented:
```ts
export async function createTodo(input: unknown) {
  const parsed = CreateTodoSchema.safeParse(input);
  if (!parsed.success) return { error: parsed.error.flatten() };

  const existing = await db.todos.count({ where: { userId: parsed.data.userId } });
  if (existing >= MAX_TODOS) return { error: { root: ["Todo limit reached"] } };

  const todo = await db.todos.create({ data: parsed.data });
  revalidateTag("todos");
  return { data: todo };
}
```

**One level of abstraction per function** — don't mix high-level orchestration with low-level implementation:
```ts
// GOOD: orchestration function calls well-named sub-functions
export async function processOrder(orderId: string) {
  const order = await validateOrder(orderId);
  const payment = await chargePayment(order);
  await updateInventory(order.items);
  await sendConfirmation(order, payment);
  return { success: true };
}
```

### Comments

- Explain **why**, never **what** — the code shows what
- No JSDoc on obvious functions — `function add(a: number, b: number): number` needs no comment
- Use comments for **non-obvious business rules**, **workarounds**, and **architecture decisions**
- TODO comments must include a tracking issue: `// TODO(JIRA-123): Migrate to v2 API`

### Error Handling

- Never swallow errors silently — surface to error boundary or return typed result
- Server Actions return `{ data }` or `{ error }` — never throw (unless intentionally caught by `error.tsx`)
- Use `error.tsx` per route segment for catch-all error boundaries
- TanStack Query's `throwOnError` option pushes errors to the nearest error boundary
- TanStack DB optimistic mutations auto-rollback on failure — handle the rollback UX

---

## Code Smells in Next.js / React

### God Component
**Symptom:** Component over 200 lines, multiple responsibilities.
**Fix:** Extract sub-components, hooks, services. Apply SRP.

### Prop Drilling Beyond 2 Levels
**Symptom:** Props passed through 3+ components unchanged.
**Fix:** Use context (React 19 `<Context value={...}>`), Zustand store, or TanStack DB live query.

### `"use client"` at Layout/Page Level
**Symptom:** Entire subtree forced into client rendering.
**Fix:** Push `"use client"` to the smallest leaf component that needs interactivity.

### Fetching in Client Components
**Symptom:** `useEffect` + `fetch` in a component when data could come from the server.
**Fix:** Fetch in Server Component, pass as props. Or prefetch in Server Component, read via `useSuspenseQuery`.

### Business Logic in Components
**Symptom:** Filtering, sorting, calculation, validation inside JSX or event handlers.
**Fix:** Extract to a service function in the feature's `services/` folder.

### Missing Offline States
**Symptom:** Component only handles loading and success — no offline or error UI.
**Fix:** Always handle four states: loading, success, error, offline. Use Serwist fallback + TanStack DB for data availability.

### Duplicated Zod Schemas
**Symptom:** Same shape defined in multiple files with slight variations.
**Fix:** Single source schema, derive variants with `.pick()`, `.partial()`, `.omit()`, `.extend()`.

### Manual Memoization with React Compiler Enabled
**Symptom:** `useMemo`, `useCallback`, `React.memo` when React Compiler is active.
**Fix:** Remove manual memoization. The compiler handles it better and consistently.

### `any` Without Justification
**Symptom:** `any` used to silence TypeScript.
**Fix:** Use `unknown` + type guards, Zod parsing, or explicit `// @ts-expect-error — [reason]` with tracking issue.

### Zustand God Store
**Symptom:** One store with 20+ state fields spanning multiple domains.
**Fix:** Split into domain slices using the slices pattern. Each slice is independently testable.

### Stale Cache Without Invalidation Strategy
**Symptom:** `"use cache"` without `cacheTag()` or `cacheLife()` — no way to invalidate.
**Fix:** Every cached function must have explicit `cacheLife()` and `cacheTag()` for invalidation.

### Direct CouchDB Writes from Browser
**Symptom:** Client code writes to CouchDB HTTP API directly.
**Fix:** Always write to PouchDB/TanStack DB locally. Replication handles server sync.

---

## Design Patterns

### Strategy Pattern
**Use for:** Pluggable sync engines, conflict resolution strategies, caching policies.

```ts
interface ConflictResolver<T> {
  resolve(local: T, remote: T, base: T): T;
}

class LastWriteWinsResolver<T extends { updatedAt: string }> implements ConflictResolver<T> {
  resolve(local: T, remote: T): T {
    return new Date(local.updatedAt) > new Date(remote.updatedAt) ? local : remote;
  }
}

class FieldMergeResolver<T extends Record<string, unknown>> implements ConflictResolver<T> {
  resolve(local: T, remote: T, base: T): T {
    // field-level three-way merge
  }
}
```

### Observer Pattern
**Use for:** Cross-slice communication, sync state notifications.

TanStack DB live queries implement this natively — UI components subscribe to collection changes automatically. For custom cross-slice events:

```ts
// shared/events/event-bus.ts
type EventMap = {
  "todo:created": Todo;
  "sync:status-changed": "online" | "offline" | "syncing";
};

class TypedEventBus {
  private listeners = new Map<string, Set<Function>>();

  on<K extends keyof EventMap>(event: K, fn: (data: EventMap[K]) => void) {
    if (!this.listeners.has(event)) this.listeners.set(event, new Set());
    this.listeners.get(event)!.add(fn);
    return () => this.listeners.get(event)?.delete(fn);
  }

  emit<K extends keyof EventMap>(event: K, data: EventMap[K]) {
    this.listeners.get(event)?.forEach((fn) => fn(data));
  }
}

export const eventBus = new TypedEventBus();
```

### Factory Pattern
**Use for:** Creating configured TanStack DB collections, Zustand stores with different defaults.

```ts
function createEntityCollection<T extends { id: string }>(
  name: string,
  schema: z.ZodType<T>,
  apiPath: string,
) {
  return createQueryCollection<T>({
    getKey: (item) => item.id,
    getId: (item) => item.id,
    schema,
    query: queryOptions({
      queryKey: [name],
      queryFn: () => fetch(apiPath).then((r) => r.json()),
    }),
    onInsert: (item) => fetch(apiPath, { method: "POST", body: JSON.stringify(item) }).then((r) => r.json()),
    onUpdate: (item) => fetch(`${apiPath}/${item.id}`, { method: "PATCH", body: JSON.stringify(item) }).then((r) => r.json()),
    onDelete: (item) => fetch(`${apiPath}/${item.id}`, { method: "DELETE" }).then(() => undefined),
  });
}

export const todoCollection = createEntityCollection("todos", TodoSchema, "/api/todos");
export const projectCollection = createEntityCollection("projects", ProjectSchema, "/api/projects");
```

### Compound Component Pattern
**Use for:** Complex UI components with shared state (tabs, accordions, forms).

```tsx
"use client";
const TabsContext = createContext<TabsState | null>(null);

function Tabs({ children, defaultValue }: TabsProps) {
  const [active, setActive] = useState(defaultValue);
  return (
    <TabsContext value={{ active, setActive }}>
      <div role="tablist">{children}</div>
    </TabsContext>
  );
}

function TabTrigger({ value, children }: TabTriggerProps) {
  const { active, setActive } = use(TabsContext)!;
  return (
    <button role="tab" aria-selected={active === value} onClick={() => setActive(value)}>
      {children}
    </button>
  );
}

function TabContent({ value, children }: TabContentProps) {
  const { active } = use(TabsContext)!;
  if (active !== value) return null;
  return <div role="tabpanel">{children}</div>;
}

Tabs.Trigger = TabTrigger;
Tabs.Content = TabContent;
export { Tabs };
```

### Adapter Pattern
**Use for:** Normalizing different API response shapes into your Zod schema.

```ts
// adapters/shopify-adapter.ts
function adaptShopifyProduct(raw: ShopifyProduct): Product {
  return ProductSchema.parse({
    id: raw.id.toString(),
    title: raw.title,
    price: parseFloat(raw.variants[0].price),
    image: raw.images[0]?.src ?? null,
    updatedAt: raw.updated_at,
  });
}
```

### Repository Pattern
**Use for:** Abstracting data access behind a clean interface, making sync engine swappable.

```ts
interface TodoRepository {
  findAll(filter?: TodoFilter): Promise<Todo[]>;
  findById(id: string): Promise<Todo | null>;
  create(input: CreateTodoInput): Promise<Todo>;
  update(id: string, input: UpdateTodoInput): Promise<Todo>;
  delete(id: string): Promise<void>;
}

class TanStackDBTodoRepository implements TodoRepository {
  constructor(private collection: typeof todoCollection) {}

  async findAll(filter?: TodoFilter) {
    // Use collection's live query internally
  }

  async create(input: CreateTodoInput) {
    return this.collection.insert({ id: crypto.randomUUID(), ...input, completed: false });
  }
  // ...
}
```

---

## Additional Principles

### DRY — Don't Repeat Yourself
Extract shared logic into hooks, services, or utilities. But don't over-DRY — if two pieces of code change for different reasons, duplication is better than wrong abstraction.

### KISS — Keep It Simple
Prefer the simplest solution that works. Don't reach for TanStack DB when `useSuspenseQuery` alone suffices. Don't add PouchDB sync when the app doesn't need offline writes.

### YAGNI — You Aren't Gonna Need It
Don't build abstractions for hypothetical future requirements. Add complexity when the need is real and present.

### Law of Demeter (Principle of Least Knowledge)
A component should only talk to its immediate dependencies. Don't reach through objects: `user.address.city.name` — destructure or pass the needed value directly.

### Separation of Concerns
- **Server Components**: data fetching, access control, SEO metadata
- **Client Components**: interactivity, local state, animations
- **Server Actions**: mutations, cache invalidation
- **TanStack Query**: server data lifecycle (fetching, caching, hydration)
- **TanStack DB**: client-side reactive data, optimistic mutations
- **Zustand**: ephemeral UI state (modals, filters, form drafts)
- **Serwist**: asset caching, offline shell, background sync
- **Zod**: data validation at every boundary

### Principle of Least Astonishment
Code should behave as a reader expects. Follow Next.js conventions (file-based routing, `page.tsx`, `layout.tsx`, `error.tsx`). Use standard naming. Don't create surprising side effects in getters or selectors.

### Composition Over Inheritance
React has no class inheritance. But the principle still applies: build complex behavior by composing simple pieces (hooks, components, middleware) rather than creating deep abstraction hierarchies.