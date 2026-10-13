# Stockora

Supermarket point-of-sale and inventory management.

- **Milestone 1** — project foundation, authentication with roles, validated product catalog.
- **Milestone 2** — cashier checkout, server-side pricing, transaction-safe inventory, receipts, sales history.

- `backend/` — Spring Boot 3.5 (Java 21, Maven) REST API, PostgreSQL + Flyway
- `frontend/` — React 19 + TypeScript + Vite + Tailwind CSS
- `docker-compose.yml` — PostgreSQL (plus Redis and the two app containers)

## Quick start (Docker)

Requires Docker with Compose.

```bash
cp .env.example .env
# edit .env: set JWT_SECRET (openssl rand -base64 48), ADMIN_EMAIL and ADMIN_PASSWORD (12+ chars)
docker compose up --build
```

| Service  | URL                                  | Notes                                              |
|----------|--------------------------------------|----------------------------------------------------|
| frontend | http://localhost:3000                | nginx serving the built SPA, proxies `/api` to the backend |
| backend  | http://localhost:8080                | Spring Boot API                                    |
| swagger  | http://localhost:8080/swagger-ui.html | OpenAPI docs (`/v3/api-docs` for the raw spec)     |
| postgres | localhost:5433                       | host port 5433 avoids clashing with a local Postgres on 5432 |

Sign in at http://localhost:3000 with `ADMIN_EMAIL` / `ADMIN_PASSWORD` from your `.env`.
Stop with `docker compose down` (add `-v` to also delete the database volume).

The stack **refuses to start without `JWT_SECRET`** — there is no default signing key in Compose.

## Running without Docker (development)

Start only the database, then run the two apps:

```bash
docker compose up -d postgres

# backend (Java 21)
cd backend
DB_PORT=5433 JWT_SECRET=$(openssl rand -base64 48) \
ADMIN_EMAIL=admin@example.com ADMIN_PASSWORD='a-long-dev-password' \
./mvnw spring-boot:run

# frontend (Node 22+), in another terminal
cd frontend
npm ci
npm run dev        # http://localhost:5173, proxies /api to http://localhost:8080
```

If port 8080 is taken, point the dev server elsewhere: `VITE_PROXY_TARGET=http://localhost:8081 npm run dev`.

## Environment variables

| Variable | Used by | Default | Purpose |
|---|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | backend | `localhost`, `5432`, `pos` | PostgreSQL connection |
| `DB_USER`, `DB_PASSWORD` | backend, compose | `pos` / `pos` | Database credentials (dev defaults — change outside local use) |
| `JWT_SECRET` | backend, compose | none in Compose; a dev placeholder (with a startup warning) when running the backend directly | HMAC key for access tokens, 32+ bytes |
| `JWT_EXPIRATION_MS` | backend | `86400000` (24 h) | Access-token lifetime |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | backend | unset | First administrator, see below |
| `CORS_ALLOWED_ORIGINS` | backend | `http://localhost:5173,http://localhost:3000` | Browser origins allowed to call `/api/**` |
| `SERVER_PORT` | backend | `8080` | HTTP port |
| `TAX_DEFAULT_RATE_PERCENT`, `TAX_STANDARD_RATE_PERCENT` | backend | `0`, `0` | Tax rate (%) for unknown tax classes / the `STANDARD` class (see [Pricing and tax](#pricing-and-tax)) |
| `TAX_PRICES_INCLUDE_TAX` | backend | `false` | `true` if catalog prices already contain tax |
| `DISCOUNT_CASHIER_MAX_PERCENT`, `DISCOUNT_MANAGER_MAX_PERCENT`, `DISCOUNT_ADMIN_MAX_PERCENT` | backend | `15`, `50`, `100` | Largest order discount each role may give |
| `STORE_NAME`, `STORE_ADDRESS`, `STORE_PHONE`, `STORE_TAX_ID`, `STORE_RECEIPT_FOOTER` | backend | name/footer have defaults | Printed on receipts |
| `VITE_API_URL` | frontend build | `/api` | API base URL baked into the bundle |
| `VITE_PROXY_TARGET` | frontend dev server | `http://localhost:8080` | Where `npm run dev` proxies `/api` |
| `VITE_CURRENCY` | frontend build | `INR` | ISO currency code used to format prices (default: Indian rupees, ₹) |
| `VITE_LOCALE` | frontend build | `en-IN` | Locale for number and date formatting (lakh/crore digit grouping, e.g. ₹1,23,456.00) |

Secrets live in `.env` (git-ignored). Nothing in the repository contains a usable credential.

## Authentication and roles

- `POST /api/auth/login` with email + password returns a JWT access token. Passwords are stored as BCrypt hashes.
- Send `Authorization: Bearer <token>` on every other request. Invalid or expired tokens get a `401` with an explicit message; the frontend then signs the user out and explains why.
- Roles are hierarchical: **ADMIN** ⊃ **MANAGER** ⊃ **CASHIER**. See [Permissions](#permissions) for what each can do.

### Creating the first administrator

No user is created by a migration. On startup the backend creates an `ADMIN` when `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set **and** no account with that email exists:

- the password must be at least 12 characters, otherwise startup fails;
- an existing account is never modified, so changing the variables later does not reset a password;
- leave both unset to create nothing (for example, in an environment where users are provisioned another way).

There is no user-management UI yet; additional users are inserted into the `users` table (BCrypt hash in `password_hash`).

## API

Interactive docs: `/swagger-ui.html`. List endpoints return
`{ content, page, size, totalElements, totalPages }`; errors return
`{ timestamp, status, error, message, fieldErrors? }` where `fieldErrors` maps request fields to messages.

| Method | Path | Min. role | Description |
|---|---|---|---|
| POST | `/api/auth/login` | public | Email + password login |
| GET | `/api/auth/me` | any | Current user |
| GET | `/api/categories` | CASHIER | List/paginate; `search`, `active`, `page`, `size` |
| GET | `/api/categories/{id}` | CASHIER | Category details |
| POST | `/api/categories` | MANAGER | Create (name unique, case-insensitive) |
| PUT | `/api/categories/{id}` | MANAGER | Update |
| PATCH | `/api/categories/{id}/status` | MANAGER | `{ "active": true\|false }` |
| GET | `/api/products` | CASHIER | Search, filter, sort, paginate (below) |
| GET | `/api/products/{id}` | CASHIER | Product details |
| GET | `/api/products/barcode/{barcode}` | CASHIER | Exact barcode lookup |
| POST | `/api/products` | MANAGER | Create; stock starts at 0 |
| PUT | `/api/products/{id}` | MANAGER | Update; never changes stock |
| PATCH | `/api/products/{id}/status` | MANAGER | `{ "active": true\|false }` |
| GET | `/api/pos/products?query=` | CASHIER | Search active products by name, SKU or barcode (max 20) |
| GET | `/api/pos/products/barcode/{barcode}` | CASHIER | Exact barcode/SKU lookup; returns inactive products with `active=false` |
| POST | `/api/sales/preview` | CASHIER | Server-calculated totals for a basket; nothing is saved |
| POST | `/api/sales/checkout` | CASHIER | Complete a sale. **Requires an `Idempotency-Key` header** |
| GET | `/api/sales/summary` | CASHIER | Count and total of completed sales in `[from, to)` by payment method (cashiers: own sales; MANAGER+: all) |
| GET | `/api/sales/my-sales` | CASHIER | The caller's own sales (paged, filterable) |
| GET | `/api/sales` | MANAGER | All sales (paged; `saleNumber`, `from`, `to`, `cashierId`, `paymentMethod`, `status`) |
| GET | `/api/sales/{id}` | CASHIER* | Sale detail |
| GET | `/api/sales/receipt/{saleNumber}` | CASHIER* | Receipt data (store details + stored sale) |
| POST | `/api/inventory/adjustments` | MANAGER | Receive or correct stock; reason required |
| GET | `/api/inventory/transactions` | MANAGER | Inventory ledger, newest first (`productId`, `saleId`) |
| GET | `/api/health` | public | Liveness |

\* Cashiers can only open their own sales; another cashier's sale returns 404. Managers and admins can open any.

`GET /api/products` parameters: `search` (name, SKU or barcode, case-insensitive contains),
`categoryId`, `active`, `lowStock`, `sortBy` (`name`, `sku`, `price`, `stock`, `createdAt`),
`direction` (`asc`/`desc`), `page` (0-based), `size` (max 100).

Business rules enforced by both the API and the database: SKU is unique case-insensitively
(stored upper-case); barcode is optional but unique when present; price > 0; cost price and
low-stock threshold ≥ 0; prices have at most 2 decimals; a product needs an existing, active
category. `lowStock` is true when `stockQuantity <= lowStockThreshold`.

## Point of sale (cashier workflow)

1. Open **Point of sale** from the sidebar or the dashboard button. The barcode field is focused.
2. **Scan** with a USB scanner (it types the digits and presses Enter), or type a name/SKU/barcode.
   Enter on an exact barcode or SKU adds the product immediately; typing shows debounced matches
   to click. Press **F2** to return to the field. Unknown, inactive and out-of-stock products give
   a clear message and are not added.
3. Edit the **cart**: +/− or type a quantity, remove a line, or clear the cart (asks to confirm).
   Countable units (piece, packet, box, dozen) accept whole numbers only; weighed/volume units
   accept up to 3 decimals. Quantities cannot exceed the stock currently shown. The cart lives only
   in the browser: adding or removing items never changes stock.
4. Totals come from the server (`/api/sales/preview`) and update as the cart changes. Optionally
   enter an **order discount %** (capped by your role).
5. **Checkout** shows the items, totals and payment choice. **Cash**: enter the amount tendered
   (the sale cannot complete if it is short; change due is shown). **UPI / Card**: record that you
   confirmed the payment.
6. On success the sale number, total and change due are shown and the cart is cleared (only now).
   **Print receipt** opens the printable receipt; **New sale** resets the screen. If checkout fails
   the cart is kept and the reason is shown.

> UPI and card payments are **recorded as confirmed by the cashier**. Nothing is verified with a
> bank or payment provider, and no card numbers, CVVs, UPI PINs or credentials are collected or stored.

Stock must exist before it can be sold. Managers add it from **Products → Stock**, which writes a
signed `ADJUSTMENT` entry (reason required) to the ledger.

## How checkout works

`POST /api/sales/checkout` runs as a single database transaction in `CheckoutService`:

1. Validate the `Idempotency-Key` and take a transaction-scoped advisory lock on it.
2. If a sale already exists for that key: return it (same request) or reject (different request).
3. Reject duplicate lines and a discount above the caller's role limit.
4. `SELECT … FOR UPDATE` the products, **ordered by id**.
5. For each line verify: exists, active, whole-number rule for the unit, enough stock.
6. Price everything on the server (see below). Client-sent prices and totals are ignored.
7. Validate payment: cash needs `amountTendered ≥ total` (change is computed); UPI/card must not send one.
8. Insert the sale, item snapshots and payment; decrement stock; append one `SALE` ledger entry per line.
9. Commit. Any failure rolls back **all** of it, and because the key is stored inside this same
   transaction a failed attempt never consumes it.

### Stock locking and overselling

Product rows are locked with `FOR UPDATE` before stock is read, so a second checkout for the same
product waits for the first to commit and then sees the reduced stock. Locks are always taken in
ascending product-id order so overlapping baskets cannot deadlock. `products.stock_quantity >= 0`
is a database `CHECK` constraint as a last line of defence. Frontend stock checks are only a
convenience.

### Idempotency

The client sends a unique `Idempotency-Key` per checkout attempt (the UI generates one per
attempt and reuses it only when the *same* basket and payment are retried). The key is stored on
the sale with a SHA-256 of the logical request and a unique index:

| Situation | Result |
|---|---|
| Same key, same request | `200` + `Idempotent-Replay: true`, the original sale |
| Same key, different request (or different user) | `409 IDEMPOTENCY_KEY_REUSED` |
| Same key sent concurrently | One `201`; the others wait on the advisory lock and then get the `200` replay |
| Earlier attempt failed | Key is free; the retry runs normally |

### Inventory ledger

`inventory_transactions` is append-only (a database trigger rejects UPDATE and DELETE).
`quantity_change` is **signed**: sales are negative; adjustments may be positive (stock received)
or negative (damage). `stock_after = stock_before + quantity_change` is enforced by a constraint.
Mistakes are fixed by a new compensating adjustment, never by editing history.

### Immutable sales

`sale_items` copy the product's name, SKU, barcode, unit and price at sale time, plus the tax rate
and amounts. A trigger blocks UPDATE/DELETE on `sale_items` and `payments`, and DELETE on `sales`,
so receipts and reports never change when the catalog is edited. Products referenced by a sale
cannot be deleted (foreign key); deactivate them instead.

## Design system

The UI is a deliberate design, not a component-library default. Tokens live in
`frontend/src/index.css` (`@theme`) and everything else consumes them.

- **Palette:** warm paper background, deep bottle-green brand, marigold used sparingly for emphasis
  (primary action on the POS, change due), chilli red only for danger/out-of-stock. Tailwind's
  `slate`/`emerald`/`amber`/`red` scales are remapped to these, so utilities stay consistent.
- **Type:** Bricolage Grotesque for headings and money, Geist for UI, Geist Mono for SKUs and
  receipts, all self-hosted via `@fontsource-variable` (works offline in a shop). Figures use
  tabular numerals so columns and totals align.
- **POS ticket:** the cart is a till-roll ticket with torn edges and an oversized total; cash
  payment offers Exact and Indian note shortcuts (₹50, ₹100, ₹200, ₹500, ₹2000).
- **Responsive:** the sidebar becomes a drawer below 1024 px; the ticket stays beside the scanner
  from tablet width.
- **Accessibility:** visible focus rings, labelled controls, `aria-live` scan feedback, keyboard
  shortcut F2, and `prefers-reduced-motion` support.

## Pricing and tax

All money is `BigDecimal` with scale 2 and **HALF_UP** rounding, done in one place
(`PricingService`), which prices both the preview and the real checkout.

1. `lineSubtotal = round(unitPrice × quantity)`.
2. `discountTotal = round(sum(lineSubtotal) × discount% / 100)`, allocated to lines in proportion;
   the last line absorbs the rounding remainder so lines always add up to the total.
3. Tax is computed **per line** on `lineSubtotal − discount` and rounded per line.
   - Tax-exclusive (default): `tax = round(taxable × rate)`, `lineTotal = taxable + tax`.
   - Tax-inclusive (`TAX_PRICES_INCLUDE_TAX=true`): `tax = round(taxable − taxable ÷ (1 + rate))`, `lineTotal = taxable`.
4. Totals are sums of the rounded line figures, so a receipt always adds up.

**Configuration.** Each product has a tax class (`STANDARD` by default, `ZERO` for zero-rated).
Rates (percent) come from configuration: `TAX_STANDARD_RATE_PERCENT` for `STANDARD`, `ZERO` is
0, and any other class uses `TAX_DEFAULT_RATE_PERCENT`. **The default is 0%**, i.e. no tax is
charged until you configure one. Rates live in `application.yml` under `pos.pricing.tax`. The
setting that applied is stored on each sale, so changing configuration never alters old receipts.

> These are mechanisms, not tax advice. Which rates, rounding method (per line vs per invoice)
> and inclusive/exclusive display are legally required where you operate has **not** been verified;
> confirm with a local tax professional before real trading.

## Permissions

| Capability | CASHIER | MANAGER | ADMIN |
|---|---|---|---|
| Search products, barcode lookup, preview, checkout | ✓ | ✓ | ✓ |
| Order discount limit (configurable) | 15% | 50% | 100% |
| View own sales and receipts | ✓ | ✓ | ✓ |
| View all sales, filter by cashier | ✗ | ✓ | ✓ |
| Create/edit products and categories | ✗ | ✓ | ✓ |
| Adjust stock, read the inventory ledger | ✗ | ✓ | ✓ |

All of this is enforced in the backend (`@PreAuthorize` plus ownership checks in
`SaleQueryService`); hiding buttons in the UI is only a convenience. There is intentionally **no**
endpoint to void, refund or edit a completed sale in this milestone.

## Receipts

Receipts are rendered from the *stored* sale (never live product data), so a reprint is identical
to the original and does not modify anything. The receipt page (`/receipt/{saleNumber}`) is
outside the app shell; print styles hide the toolbar and everything else, and a paper selector
switches between 58 mm and 80 mm layouts (`@page size` follows the selection). Add `?print=1` to
open the browser print dialog automatically. Store name, address, phone, tax ID and the footer
message come from the `STORE_*` settings.

## Logging

Structured `key=value` log lines (no tokens, passwords or payment data): `checkout.attempt`,
`checkout.completed`, `checkout.rejected`, `checkout.inventory_conflict` (insufficient stock or
inactive product), `checkout.idempotent_replay`, and `inventory.adjusted`. Unexpected errors are
logged with a stack trace server-side and returned to the client as a generic message.

## Database and migrations

Flyway runs automatically at backend startup (`backend/src/main/resources/db/migration`).
Hibernate is set to `ddl-auto: validate`, so it never changes the schema. Money uses
`NUMERIC(12,2)`; quantities use `NUMERIC(12,3)`.

| Migration | Purpose |
|---|---|
| V1–V12 | Core schema: categories, products, inventory, pricing, customers, registers, shifts, sales, audit log |
| V13 | `users` table |
| V14 | Development register `REG-1` (and demo users, removed by V16) |
| V15 | Sample categories and products for development |
| V16 | `SUPERVISOR` → `MANAGER`; removes the well-known V14 demo accounts |
| V17 | Category/product Milestone 1 columns, case-insensitive category names, `pg_trgm` search index |
| V18 | Case-insensitive unique SKU index |
| V19 | Sales model: `sales` / `sale_items` / `payments` with immutable snapshots, sale-number sequence, idempotency key, append-only `inventory_transactions` ledger, immutability triggers |

Never edit an applied migration; add a new `V<n>__*.sql`. V17 enables the `pg_trgm`
extension, which requires a role allowed to `CREATE EXTENSION` (the Compose database user is).

## Tests

```bash
# backend: unit tests (Mockito) + integration tests (MockMvc against a real PostgreSQL
# via Testcontainers, running the real Flyway migrations). Docker must be running.
cd backend && ./mvnw test

# frontend: Vitest + React Testing Library, then type check and lint
cd frontend && npm test && npm run typecheck && npm run lint
```

Results of the last full run (Milestone 2 sign-off):

| Suite | Result |
|---|---|
| Backend `./mvnw test` | **117 passed**, 0 failed |
| Frontend `npm test` | **72 passed**, 0 failed (10 files) |
| Frontend `tsc --noEmit`, `oxlint`, `vite build` | clean |

What the backend suite covers beyond Milestone 1: pricing/rounding (9 unit tests), checkout rules
and totals (25), atomic rollback with faults injected inside PostgreSQL (3), concurrency and
idempotency under real parallel requests (9), and sale access/filters (9).

The concurrency tests were mutation-checked: removing the product row lock makes three of them
fail (oversell and ordering), and removing the idempotency advisory lock makes the same-key test
fail. They are not passing vacuously.

Also verified by hand in a browser against the Docker stack: unknown-barcode feedback, repeated
scan raising quantity, short-cash blocking, a completed cash sale (stock 20 → 18 exactly once,
ledger entry, snapshot and payment rows), the receipt page, and the sales history list. Print
output itself (the browser print dialog / a physical thermal printer) was **not** exercised.

## Project layout

```
backend/src/main/java/com/pos/
  auth/        login, JWT, role model, first-admin bootstrap
  catalog/     categories & products: entity, dto, repository, service, web, exception
  common/      security + CORS + OpenAPI config, error handling, shared DTOs
  sales/       checkout, pricing engine, preview, POS lookup, sales history, receipts
  inventory/   append-only ledger, stock adjustments
  registers/, pricing/, customers/, realtime/, reporting/
               groundwork for later milestones (see Known limitations)
backend/src/main/resources/db/migration/   Flyway migrations
frontend/src/
  components/  ui primitives, ProductForm, CategoryForm, toast,
               pos/ (barcode input, search results, cart, checkout dialog), receipt/, sales/
  layouts/     AppLayout (sidebar + top bar)
  pages/       Login, Dashboard, ProductList, ProductForm, Categories,
               Pos, SalesHistory, SaleDetail, Receipt (standalone print view)
  router/      routes and ProtectedRoute / RequireRole guards
  services/    axios client and API modules
  store/       auth session (zustand)
  validation/  zod schemas   hooks/  types/  utils/
```

## Dependency versions

Java 21 · Spring Boot 3.5.16 · Spring Security · Spring Data JPA · Flyway 11.7 · PostgreSQL 16 ·
jjwt 0.13.0 · springdoc-openapi 2.8.9 · Testcontainers (Boot-managed) ·
React 19.3 · TypeScript 7.0 · Vite 8.3 · Tailwind CSS 4.3 · React Router 7.18 · TanStack Query 5.103 ·
Axios 1.20 · React Hook Form 7.90 · Zod 3.25 · Zustand 5 · Vitest 5 · Testing Library (react 16.3).

## Known limitations

- **No voids, refunds or returns.** The `VOIDED` status exists in the model but nothing sets it. Doing this correctly needs compensating stock entries and approvals (Milestone 3).
- Payments are cashier-recorded only: no gateway, no reconciliation, no split tenders (one payment per sale) and no partial payments.
- Discounts are order-level percentages. There are no per-item discounts, promotions or coupons, and no customer attached to a sale.
- Stock is a single quantity per product. Batches/expiry (the legacy `inventory` table), purchasing and stock-takes are not implemented; the only inventory writes are sales and manager adjustments.
- Tax is configuration-driven with no legal validation (see [Pricing and tax](#pricing-and-tax)); one rate per tax class and no tax-class editor in the UI.
- Sale numbers use a database sequence and the UTC date, so numbers can have gaps and a late-evening sale in a UTC+ time zone may carry the previous day's date.
- Receipts print through the browser. Direct thermal-printer/ESC-POS integration, cash-drawer kick and barcode label printing are out of scope; printing was not tested on physical hardware.
- Registers, shifts, PIN login (backend only), Redis and WebSocket groundwork remain unused by the UI. A web checkout has no register unless the session carries one.
- The access token is a single JWT kept in `localStorage`: no refresh tokens, no server-side revocation, exposed to XSS. No login rate limiting or lockout.
- There is no user-management UI; users beyond the bootstrap admin are inserted into the database.
- V15 inserts sample catalog data in every environment that runs the migrations; gate it to development before a real deployment.
- The cart is not persisted: a page refresh empties it (by design, since it is temporary state).
- The frontend ships as one JavaScript chunk (~500 kB before gzip), and there is no automated browser end-to-end suite.

## Next steps (Milestone 3)

1. Void/return workflow with manager approval and compensating ledger entries; customer receipts by reprint audit.
2. Shifts and cash-up: open/close register, expected vs counted cash, X/Z reports.
3. Purchasing and stock receipts, supplier records, stock-takes, batch/expiry using the existing `inventory` table.
4. Reports: daily sales, top products, low-stock, margin using the stored cost prices.
5. Payment-gateway integration with verified statuses and refunds; split tenders.
6. User management, login hardening (rate limiting, refresh tokens) and a Playwright smoke suite with CI.
