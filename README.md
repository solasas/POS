# Stockora

Supermarket point-of-sale and inventory management. **Milestone 1** delivers the project
foundation, authentication with roles, and a validated product catalog.

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
| `VITE_API_URL` | frontend build | `/api` | API base URL baked into the bundle |
| `VITE_PROXY_TARGET` | frontend dev server | `http://localhost:8080` | Where `npm run dev` proxies `/api` |
| `VITE_CURRENCY` | frontend build | `USD` | ISO currency code used to format prices |

Secrets live in `.env` (git-ignored). Nothing in the repository contains a usable credential.

## Authentication and roles

- `POST /api/auth/login` with email + password returns a JWT access token. Passwords are stored as BCrypt hashes.
- Send `Authorization: Bearer <token>` on every other request. Invalid or expired tokens get a `401` with an explicit message; the frontend then signs the user out and explains why.
- Roles are hierarchical: **ADMIN** ⊃ **MANAGER** ⊃ **CASHIER**. Anyone signed in can read the catalog; **MANAGER and above** can create and change products and categories.

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
| GET | `/api/health` | public | Liveness |

`GET /api/products` parameters: `search` (name, SKU or barcode, case-insensitive contains),
`categoryId`, `active`, `lowStock`, `sortBy` (`name`, `sku`, `price`, `stock`, `createdAt`),
`direction` (`asc`/`desc`), `page` (0-based), `size` (max 100).

Business rules enforced by both the API and the database: SKU is unique case-insensitively
(stored upper-case); barcode is optional but unique when present; price > 0; cost price and
low-stock threshold ≥ 0; prices have at most 2 decimals; a product needs an existing, active
category. `lowStock` is true when `stockQuantity <= lowStockThreshold`.

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

Results of the last full run (Milestone 1 sign-off):

| Suite | Result |
|---|---|
| Backend `./mvnw test` | **69 passed**, 0 failed (auth 22, catalog 38, sales 8, context 1) |
| Frontend `npm test` | **28 passed**, 0 failed (5 files) |
| Frontend `tsc --noEmit`, `oxlint`, `vite build` | clean |

Also verified by hand in a browser against the Docker stack: login (wrong and correct
password), protected-route redirect, dashboard counts, product search, create with client-side
and server-side (duplicate SKU) errors, and tablet-width layout.

## Project layout

```
backend/src/main/java/com/pos/
  auth/        login, JWT, role model, first-admin bootstrap
  catalog/     categories & products: entity, dto, repository, service, web, exception
  common/      security + CORS + OpenAPI config, error handling, shared DTOs
  sales/, registers/, inventory/, pricing/, customers/, realtime/, reporting/
               groundwork for later milestones (see below)
backend/src/main/resources/db/migration/   Flyway migrations
frontend/src/
  components/  ui primitives, ProductForm, CategoryForm, toast
  layouts/     AppLayout (sidebar + top bar)
  pages/       Login, Dashboard, ProductList, ProductForm, Categories
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

- Products and categories are deactivated rather than deleted. Products cannot be deleted through the API.
- Stock is only initialized to zero and displayed; adjustments, purchasing and sale deductions are not implemented, so every product is initially "low stock".
- The access token is a single JWT kept in `localStorage`: no refresh tokens, no server-side revocation, and it is exposed to XSS. There is no login rate limiting or account lockout.
- No user-management UI or API; users beyond the bootstrap admin are inserted directly into the database. Password recovery and social login are out of scope.
- V15 inserts sample catalog data in every environment that runs the migrations; it should be gated to development before a real deployment.
- Earlier groundwork (PIN login, register sessions, `/api/transactions` void/discount, Redis, WebSocket, the `inventory` table) is retained and tested but is not part of this milestone and is not exposed in the UI. `products.stock_quantity` and the separate `inventory` table overlap and need reconciling in Milestone 2.
- The frontend ships as one JavaScript chunk (~500 kB before gzip); route-level code splitting is not done.
- Browser behavior was verified manually; there is no automated end-to-end suite.

## Next steps (Milestone 2)

1. Inventory: stock adjustments with an audit trail, reconcile `inventory` vs `products.stock_quantity`, stock history.
2. Suppliers and purchase orders feeding stock receipts.
3. POS checkout: basket, barcode scanning via the existing lookup endpoint, payments, receipts, stock deduction.
4. User management (create/disable users, change password) and login hardening (rate limiting, refresh tokens).
5. Gate sample data to a dev profile; add a Playwright end-to-end smoke suite and CI.
