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
AUTH_EMAIL_VERIFICATION_REQUIRED=false
```

Do not commit `.env`.

`AUTH_EMAIL_VERIFICATION_REQUIRED=false` lets signup -> login work immediately in local dev without a working
SMTP account; leave it unset (defaults to `true`) for anything resembling production.

## Run With PostgreSQL

```powershell
.\scripts\start-local.ps1
```

This loads `.env`, starts PostgreSQL and Redis through Docker Compose, then runs Spring Boot.
If PostgreSQL is already listening on port `5432`, the script reuses it and only starts Redis through Docker Compose.
Postgres and Redis are both required -- authentication is backed by the real JPA `User` entity and a
Redis-stored refresh-token/blacklist, so there is no DB-free auth mode.

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
