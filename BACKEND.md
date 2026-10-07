# JB Solar backend — first API slice

This Spring Boot backend provides a secured REST API for the admin portal and vendor-agent app. It covers login, vendor agents, vendors, farmers, policy plans, and policies, including role-scoped updates, a recycle bin, and administrator Excel exports.

## Run locally

Requirements: Java 21+ and PostgreSQL. Create a database named `jbsolar`, then set environment variables before starting:

- `DB_URL` — defaults to `jdbc:postgresql://localhost:5432/jbsolar`
- `DB_HOST`, `DB_PORT`, and `DB_NAME` — alternatively configure the PostgreSQL host, port, and database name separately; `DB_URL` takes precedence when set
- `DB_USERNAME` and `DB_PASSWORD` — default to `postgres` / `postgres`
- `JWT_SECRET` — **required in non-local environments**, at least 32 bytes; the checked-in fallback is development-only
- `CORS_ALLOWED_ORIGINS` — comma-separated admin web origins; defaults to `http://localhost:3000`
- `APP_ADMIN_MOBILE` and `APP_ADMIN_PASSWORD` — optionally create the first admin on startup; set both together
- `DDL_AUTO` — defaults to `update` for local development; production should use versioned migrations and `validate`

Run with `mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on macOS/Linux. The app listens on port 8080 by default (`PORT` overrides it).

## Deploy on Render

1. Push this repository to GitHub, then in Render select **New > Blueprint** and connect the repository. Render reads [`render.yaml`](./render.yaml) to create the Docker web service and PostgreSQL database.
2. During setup, provide `APP_ADMIN_MOBILE` and `APP_ADMIN_PASSWORD` for the first administrator. Set `CORS_ALLOWED_ORIGINS` to the exact origin(s) of the deployed frontend, comma-separated; for example, `https://admin.example.com`. These values can also be changed in the web service's Environment settings later.
3. Deploy the Blueprint. Render generates `JWT_SECRET` and wires the PostgreSQL connection automatically. The service health check uses `/actuator/health`.
4. Open the deployed service's `https://<service-name>.onrender.com/swagger-ui/index.html` to verify the API, then log in using the administrator credentials configured above.

The Blueprint uses free Render plans as a starting point; review their current limits and upgrade if needed. It sets `DDL_AUTO=update` so a fresh database is initialized automatically. Hibernate schema updates are not a substitute for production migrations; add and apply versioned migrations before relying on this for production data. Keep the database and service in the same Render region.

## Swagger / OpenAPI

When the backend is running, open Swagger UI at `http://localhost:8080/swagger-ui/index.html`. The OpenAPI JSON document is available at `http://localhost:8080/v3/api-docs`. In Swagger UI, use **Authorize** and enter `Bearer <accessToken>` after logging in to call protected endpoints.

For tests, the `test` profile uses an in-memory H2 database; run `mvnw.cmd test`.

## Authentication

`POST /api/v1/auth/login`

```json
{ "mobile": "9000000000", "password": "your-password" }
```

Use the returned access token in `Authorization: Bearer <accessToken>`. Tokens expire after `JWT_EXPIRATION_SECONDS` (one hour by default). Passwords are stored as BCrypt hashes. Admin-only endpoints enforce the `ADMIN` role; agent data is scoped to that agent's vendor.

## Implemented endpoints

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/v1/auth/login` | Public |
| GET, POST, PUT, DELETE | `/api/v1/vendors[/{id}]` | Admin |
| PATCH | `/api/v1/vendors/{id}/status?status=ACTIVE` | Admin |
| GET, POST, PUT, DELETE | `/api/v1/agents[/{id}]` | Admin |
| GET, POST, PUT, DELETE | `/api/v1/farmers[/{id}]` | Authenticated; agents limited to their vendor |
| GET, POST, PUT, DELETE | `/api/v1/policy-plans[/{id}]` | Read: authenticated; write: admin |
| PATCH | `/api/v1/policy-plans/{id}/status?status=ACTIVE` | Admin |
| GET, POST, PUT, DELETE | `/api/v1/policies[/{id}]` | Authenticated; agents limited to their vendor |
| GET | `/api/v1/recycle-bin` | Admin |
| POST | `/api/v1/recycle-bin/{resource}/{id}/restore` | Admin |
| DELETE | `/api/v1/recycle-bin/{resource}/{id}` | Admin; permanent purge |
| GET | `/api/v1/exports` or `/api/v1/exports/{resource}` | Admin; downloads `.xlsx` |

Collection endpoints return a `PageResponse` with `content`, `page` (zero-based), `size`, `totalElements`, `totalPages`, `first`, and `last`. All list endpoints accept `page` (default `0`), `size` (default `25`, maximum `500`), `sort`, and `direction` (`asc` or `desc`; defaults to `createdAt` descending), and stable ID tie-breaking is applied. Invalid page bounds, sort fields, directions, date ranges, and status values return `400`.

Filters available to collection and export endpoints:

| Query parameter | Applies to | Meaning |
|---|---|---|
| `search` | All datasets | Case-insensitive partial search across that resource's names/identifiers and relevant text fields |
| `status` | Vendors, agents, policy plans, policies | Exact status such as `ACTIVE`, `INACTIVE`, `PENDING`, or `CANCELLED` |
| `createdFrom`, `createdTo` | All datasets | Inclusive UTC calendar-date bounds on `createdAt`, formatted `YYYY-MM-DD` |
| `vendorId` | Agents, farmers, policies | Filter to one vendor; agents cannot override their own vendor scope |
| `activeOnly` | Policy plans | Defaults to `true`; set `false` to include inactive plans or filter for `status=INACTIVE` |

For example, `/api/v1/policies?page=0&size=50&status=PENDING&search=JB-POL&sort=startDate&direction=asc` returns the first page of matching pending policies. Export endpoints accept the same filter and sorting parameters and export every matching row (rather than only the requested page), using `size` as their bounded database batch size. For `/api/v1/exports` (all sheets), the shared sort fields are `createdAt`, `updatedAt`, `id`, and `status`; the Farmers sheet has no status field, so a status sort leaves that sheet ordered by creation date. A status filter applies only to sheets with a status column; Farmers has no status property.

Agent registration takes `vendorId`, `fullName`, `mobile`, and `initialPassword`. Farmer creation derives the creating agent from the token; clients cannot set ownership. Policy creation retrieves plan pricing from the database, computes GST on the server, and starts the policy as `PENDING`.

DELETE on vendors, agents, farmers, policy plans, and policies moves records to the recycle bin by setting `deleted_at`; it does not cascade through foreign keys. Deleted rows are omitted from normal lists and cannot be used in new policies. The administrator can restore a record or permanently purge it. Purge is rejected with `409 Conflict` when modeled child records still refer to it; purge dependent records first, and database constraints also protect references such as payments and invoices. This keeps policy/payment history safe across multiple joins. Restoring a vendor does not restore separately deleted agents or records. Only pending policies can be edited; prices and GST are recalculated from the selected active plan.

Excel exports include active (non-recycled) rows only. `/api/v1/exports` creates one workbook with Vendors, Agents, Farmers, Policy plans, and Policies sheets; the singular-resource endpoint creates a workbook containing that data sheet. Exports are administrator-only, use paged database reads and streaming workbook output, and are capped at 100,000 rows per sheet. Responses disable browser caching.

## Deliberately not enabled yet

A policy remains pending until a payment gateway integration verifies a signed callback. Do not activate policies from a client request. Payment orders/webhook verification, invoices/PDFs, pumps/installations, S3 uploads, service requests/visits, audit logs, rate limiting, and production database migrations still need their own implementations and tests. For an existing database, deploy a versioned migration adding nullable `deleted_at` columns and their recycle-bin indexes to `vendors`, `vendor_agents`, `farmers`, `policy_plans`, and `policies`; the supplied SQL is the fresh-schema baseline. JPA's local `update` mode is only for development, not a production migration strategy.
