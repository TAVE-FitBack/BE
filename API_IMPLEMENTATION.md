# Fitback Backend API Implementation

## Overview

This backend implements the 55 HTTP routes documented in `api_spec (1).html`.
It provides authentication, tenant isolation, store management, CRM workflows,
AI analysis, follow-up messaging, events, enrollments, dashboards, and reports.

Base URL:

```text
/api/v1
```

## Architecture

The current implementation follows a layered DDD foundation:

```text
core/
  application/
    port/             Application-owned repository and AI contracts
  infrastructure/    In-memory tenant repository and FastAPI/LLM adapter
  presentation/      REST API adapter
global/
  config/            Spring Security configuration
  error/             Shared API exception handling
  security/          JWT, tenant identity, and webhook security
```

Application services depend on ports rather than concrete infrastructure.
Tenant-owned data is isolated using the `storeId` claim from signed JWT access
tokens.

Full decomposition into explicit identity, CRM, engagement, campaign,
conversion, and insight bounded-context packages is tracked in GitHub issue #6.

## Implemented Domains

- Authentication: register, login, refresh rotation, logout, password reset
- Store: profile, services, settings
- Customer: creation, detail, update, filtering, pagination, soft deletion
- Consultation: records, AI analysis, interest services, conversion reasons
- Follow-up: status, snooze, editing, contact results
- Messaging: AI drafts, editing, copy, send, signed delivery callbacks
- Events: campaigns, automatic targets, target delivery status
- Enrollment: registration history and status transitions
- Dashboard and reports

## Security

- All business routes require a signed Bearer JWT.
- JWT access tokens contain a `storeId` tenant claim.
- Repository operations are scoped by the authenticated tenant.
- Refresh tokens expire, rotate after use, and reject reuse.
- Delivery callbacks require `X-Webhook-Signature`.
- The application fails startup when `JWT_SECRET` is missing.

## AI Integration

AI functionality uses the `AiAnalysisPort`.

When `API_KEY_CODE` and `AI_BASE_URL` are configured, the adapter calls:

```text
POST {AI_BASE_URL}/analyze
POST {AI_BASE_URL}/messages/generate
```

Provider failures return `502 Bad Gateway`. Deterministic fallback behavior is
disabled by default and enabled only explicitly for tests or local development.

## Environment Variables

Copy the values described in `.env.example` into the runtime environment:

```dotenv
API_KEY_CODE=
AI_BASE_URL=http://localhost:8000
JWT_SECRET=replace-with-at-least-32-byte-secret
WEBHOOK_SECRET=replace-with-provider-webhook-secret
```

Database environment variables from `application.yaml`:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/fitback
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

## Run

Windows:

```powershell
.\gradlew.bat bootRun
```

The default runtime configuration expects PostgreSQL and the required
`JWT_SECRET`.

## Test And Build

Run the complete verification suite:

```powershell
.\gradlew.bat clean build --console=plain
```

The suite covers:

- all documented method/path mappings
- core customer workflow
- JWT authentication and tenant isolation
- refresh-token rotation and reuse rejection
- AI success/failure boundaries
- interest and reason replacement invariants
- enrollment conversion
- customer filtering and soft deletion
- provider message ID and signed callback transitions

## Current Persistence Adapter

The API currently uses an in-memory implementation of `TenantDataRepository`.
Replacing it with a PostgreSQL/JPA adapter and migrations is tracked in GitHub
issue #5. Application use cases should not need contract changes during that
migration.

## Related Issues

- #3: API specification implementation
- #4: JWT tenant isolation and webhook security
- #5: PostgreSQL persistence and migrations
- #6: Explicit bounded-context decomposition

