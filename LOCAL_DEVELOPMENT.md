# Local Development

## Environment

Copy `.env.example` to `.env` and set local values. At minimum the backend needs:

```env
JWT_SECRET=replace-with-at-least-32-byte-secret
DB_NAME=fitback
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_PORT=6379
MAIL_USERNAME=local@example.com
MAIL_PASSWORD=<local-mail-password>
```

Do not commit `.env`.

## Run With PostgreSQL

```powershell
.\scripts\start-local.ps1
```

This loads `.env`, starts PostgreSQL and Redis through Docker Compose, then runs Spring Boot.
If PostgreSQL is already listening on port `5432`, the script reuses it and only starts Redis through Docker Compose.

## Run Core API Only

If Docker image downloads are blocked, the React `/api/v1` workflow can still be tested without JPA/PostgreSQL:

```powershell
.\scripts\start-local.ps1 -CoreOnly
```

This starts `CoreLocalApplication`, which exposes the same `/api/v1` auth, customer, consultation, and dashboard routes used by the React frontend.

## Frontend

```powershell
cd tmpFE
npm run dev
```

The Vite dev server proxies `/api` to `http://localhost:8080`.

## Smoke Test

With the backend and frontend running:

```powershell
.\scripts\smoke-auth.ps1
```

The smoke test creates a real account, logs in, and calls `GET /api/v1/customers` with the returned bearer token.
