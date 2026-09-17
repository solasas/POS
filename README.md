# Supermarket POS

Monorepo for a supermarket point-of-sale system.

- `backend/` — Spring Boot 3 (Java 21, Maven) REST + WebSocket API
- `frontend/` — React 18 (JavaScript, Vite) SPA

## Run everything locally with one command

Requires Docker and Docker Compose.

```bash
docker compose up --build
```

This starts four services:

| Service    | URL                          | Notes                                  |
|------------|-------------------------------|-----------------------------------------|
| frontend   | http://localhost:3000        | Nginx serving the built React app, proxies `/api` and `/ws` to backend |
| backend    | http://localhost:8080        | Spring Boot API                        |
| postgres   | localhost:5433                | Database `pos` / user `pos` / pass `pos` (override via `.env`); mapped to 5433 on the host to avoid clashing with a locally installed Postgres |
| redis      | localhost:6379                | Available for caching/session use      |

Stop everything with `Ctrl+C`, then `docker compose down` (add `-v` to also wipe the Postgres volume).

Copy `.env.example` to `.env` first if you want to override the default DB credentials or JWT secret.

## Running services individually (without Docker)

### Backend

Requires Java 21.

```bash
cd backend
./mvnw spring-boot:run
```

By default it connects to Postgres at `localhost:5432` (see `src/main/resources/application.yml` for the env vars it reads: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`). Run just the database with:

```bash
docker compose up postgres redis
```

The dockerized Postgres is published on host port `5433` (to avoid clashing with a locally installed Postgres on the default `5432`), so point the backend at it with `DB_PORT=5433 ./mvnw spring-boot:run`.

### Frontend

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Runs the Vite dev server at http://localhost:5173. Copy `frontend/.env.example` to `frontend/.env` to point it at a backend running somewhere other than `http://localhost:8080/api`.

## Project layout

```
POS/
├── docker-compose.yml
├── backend/     Spring Boot API (com.pos.{auth,catalog,inventory,pricing,sales,registers,customers,reporting,realtime,common})
└── frontend/    React + Vite SPA (Tailwind, React Router, Zustand, React Query, axios)
```

### Backend packages

| Package      | Responsibility                                  |
|--------------|--------------------------------------------------|
| `auth`       | Authentication and JWT-based authorization       |
| `catalog`    | Product catalog: items, categories, barcodes     |
| `inventory`  | Stock levels and stock movements                 |
| `pricing`    | Pricing rules, discounts, and promotions          |
| `sales`      | POS transactions, carts, receipts, payments       |
| `registers`  | Cash register / terminal session management       |
| `customers`  | Customer profiles and loyalty                     |
| `reporting`  | Sales and inventory reporting                      |
| `realtime`   | WebSocket/STOMP config for live updates             |
| `common`     | Shared config (security, etc.) and utilities       |
# POS
