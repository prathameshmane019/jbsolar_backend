# JB Solar backend — first API slice

This Spring Boot backend provides a secured REST API for the admin portal and vendor-agent app. It covers login, vendor agents, vendors, farmers, policy plans, and policies, including role-scoped updates, a recycle bin, and administrator Excel exports.

## Run locally

Requirements: Java 21+ and PostgreSQL. Create a database named `jbsolar`, then set environment variables before starting:

- `DB_URL` — defaults to `jdbc:postgresql://localhost:5432/jbsolar`
- `DB_HOST`, `DB_PORT`, and `DB_NAME` — alternatively configure the PostgreSQL host, port, and database name separately; `DB_URL` takes precedence when set
- `DB_USERNAME` and `DB_PASSWORD` — default to `postgres` / `postgres`
- `JWT_SECRET` — **required in non-local environments**, at least 32 bytes; the checked-in fallback is development-only
- `IDENTITY_HASH_KEY` — **required in non-local environments**, at least 32 bytes and kept stable; HMACs Aadhaar numbers for unique matching without retaining Aadhaar plaintext
- `CORS_ALLOWED_ORIGINS` — comma-separated admin web origins; defaults to `http://localhost:3000`
- `SERVER_ADDRESS` — server bind address; defaults to `0.0.0.0` so a phone on the local network can reach the development server. Restrict access with your firewall.
- `AWS_ENDPOINT_URL_S3`, `AWS_REGION`, `NEON_STORAGE_BUCKET`, `AWS_ACCESS_KEY_ID`, and `AWS_SECRET_ACCESS_KEY` — configure the Neon branch's private S3-compatible Object Storage endpoint and a scoped Neon credential. Create the credential with `storage:read` and `storage:write` (write includes read and delete). Keep the credentials in deployment secrets; Spring Boot uses them only to sign and verify uploads.
- `UPLOAD_CLEANUP_DELAY_MS` — abandoned pending uploads are removed after 24 hours; cleanup runs hourly by default, configurable with this delay in milliseconds
- `DUMMY_PAYMENT_ENABLED` — disable the simulated payment-success endpoint in production; defaults to `true` for local development/demo work
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

### Expo development networking

Set the Expo app's API base URL to an address reachable from the device:

- Expo Web on the same computer: `http://localhost:8080`
- Android emulator: `http://10.0.2.2:8080`
- Expo Go on a physical phone: `http://<development-computer-LAN-IP>:8080`; keep both devices on the same Wi-Fi and allow Java/Spring Boot through the computer firewall

On a physical phone, `localhost` points to the phone, not the development computer. Native Expo requests are not subject to browser CORS. Expo Web at `http://localhost:8081` is included in the default CORS origins; if `CORS_ALLOWED_ORIGINS` is explicitly set, add that exact origin.

## Authentication

Admin web login: `POST /api/v1/auth/login`

Vendor-agent mobile login: `POST /api/v1/auth/agent/login`

```json
{ "mobile": "9000000000", "password": "your-password" }
```

Use the returned access token in `Authorization: Bearer <accessToken>`. Tokens expire after `JWT_EXPIRATION_SECONDS` (one hour by default). Passwords are stored as BCrypt hashes. Admin-only endpoints enforce the `ADMIN` role; agent data is scoped to that agent's vendor.

## Implemented endpoints

| Method | Endpoint | Access |
|---|---|---|
| POST | `/api/v1/auth/login` | Public; administrators only |
| POST | `/api/v1/auth/agent/login` | Public; vendor agents only |
| GET, POST, PUT, DELETE | `/api/v1/vendors[/{id}]` | Admin; vendor agents may GET only their own `/api/v1/vendors/{id}` company details |
| PATCH | `/api/v1/vendors/{id}/status?status=ACTIVE` | Admin |
| GET, POST, PUT, DELETE | `/api/v1/agents[/{id}]` | Admin; authenticated agents may search the collection but only see their own profile |
| GET, POST, PUT, DELETE | `/api/v1/farmers[/{id}]` | Authenticated; agents limited to their vendor |
| GET, POST, PUT, DELETE | `/api/v1/policy-plans[/{id}]` | Read: authenticated; write: admin |
| PATCH | `/api/v1/policy-plans/{id}/status?status=ACTIVE` | Admin |
| GET, POST, PUT, DELETE | `/api/v1/policies[/{id}]` | Authenticated; agents limited to their vendor |
| POST | `/api/v1/mobile/files/upload-url` | Vendor agent; own farmer or policy resource |
| POST | `/api/v1/mobile/files/{id}/complete` | Upload owner |
| GET | `/api/v1/mobile/files?resourceType=...&resourceId=...` | Vendor agent; own farmer or policy resource |
| POST | `/api/v1/policies/{policyId}/payments/dummy-order` | Vendor agent for own vendor or admin |
| POST | `/api/v1/payments/{paymentId}/simulate-success` | Authenticated; dummy payments enabled only |
| GET | `/api/v1/policies/{policyId}/invoice` | Vendor agent for own vendor or admin |
| GET | `/api/v1/invoices/{invoiceId}` | Vendor agent for own vendor or admin |
| GET | `/api/v1/recycle-bin` | Admin |
| POST | `/api/v1/recycle-bin/{resource}/{id}/restore` | Admin |
| DELETE | `/api/v1/recycle-bin/{resource}/{id}` | Admin; permanent purge |
| GET | `/api/v1/exports` or `/api/v1/exports/{resource}` | Admin; downloads `.xlsx` |

Collection endpoints return a `PageResponse` with `content`, `page` (zero-based), `size`, `totalElements`, `totalPages`, `first`, and `last`. All paged list endpoints accept `page` (default `0`), `size` (default `25`, maximum `100`), `sort`, and `direction` (`asc` or `desc`; defaults to `createdAt` descending), and stable ID tie-breaking is applied. Invalid page bounds, sort fields, directions, date ranges, and status values return `400`. Farmer collection search also matches an exact 12-digit Aadhaar number against its keyed HMAC; Aadhaar plaintext is never stored or returned.

Policy create and update requests may include optional `pumpPowerHp` and `motorHeadMeters` decimal values. Both must be positive when provided and support up to six integer digits and two decimal places; policy responses and exports include them. They are policy/pump-set details, not farmer details. For example, include `"pumpPowerHp": 5.5` and `"motorHeadMeters": 100` in a policy request.

Filters available to collection and export endpoints:

| Query parameter | Applies to | Meaning |
|---|---|---|
| `search` | All datasets | Case-insensitive partial search across that resource's names/identifiers and relevant text fields |
| `status` | Vendors, agents, policy plans, policies | Exact status such as `ACTIVE`, `INACTIVE`, `PENDING`, or `CANCELLED` |
| `createdFrom`, `createdTo` | All datasets | Inclusive UTC calendar-date bounds on `createdAt`, formatted `YYYY-MM-DD` |
| `vendorId` | Agents, farmers, policies | Filter to one vendor; agents cannot override their own vendor scope |
| `activeOnly` | Policy plans | Defaults to `true`; set `false` to include inactive plans or filter for `status=INACTIVE` |

For example, `/api/v1/policies?page=0&size=50&status=PENDING&search=JB-POL&sort=startDate&direction=asc` returns the first page of matching pending policies. Export endpoints accept the same filter and sorting parameters and export every matching row (rather than only the requested page), using `size` as their bounded database batch size. For `/api/v1/exports` (all sheets), the shared sort fields are `createdAt`, `updatedAt`, `id`, and `status`; the Farmers sheet has no status field, so a status sort leaves that sheet ordered by creation date. A status filter applies only to sheets with a status column; Farmers has no status property.

Agent registration takes `vendorId`, `fullName`, `mobile`, and `initialPassword`. Ten-digit Indian agent numbers are canonicalized to `+91...`; international numbers with an explicit country code retain E.164 form. Numbers are normalized before insert/login and protected by the database uniqueness constraint. Farmer registration requires a 12-digit `aadhaarNumber`; the server stores only a keyed HMAC-SHA256 digest with a unique index, never returns the number, and requires a stable secret via `IDENTITY_HASH_KEY`. Farmer creation derives the creating agent from the token; clients cannot set ownership. Policy creation retrieves plan pricing from the database, computes GST on the server, and starts the policy as `PENDING`.

For farmer documents, request an upload URL with `resourceType=FARMER`, farmer ID, file name, MIME type, and size (maximum 10 MiB); `purpose` is optional and defaults to `FARMER_DOCUMENT`. For policy evidence, use `resourceType=POLICY` with the policy ID and `purpose=CUSTOMER_SIGNATURE` or `purpose=PUMP_SET_IMAGE`; these purposes accept image MIME types only. The agent JWT must own the policy's vendor. The backend creates a server-generated key and a pending metadata record, then returns `fileId`, `uploadUrl`, and `expiresAt`. The URL is HTTPS, expires after 10 minutes, and is signed for the exact object, declared size, and content type. The app PUTs raw image bytes directly to the configured Neon Object Storage bucket with the signed `Content-Type`, then calls the completion endpoint with the `fileId`. Completion verifies the object's size and content type in Neon before marking the record uploaded; repeat completion returns the same metadata without rechecking storage. Pending records and any objects at their keys are cleaned up after 24 hours. The API response includes `id`, `resourceType`, `resourceId`, `purpose`, `originalFilename`, `contentType`, `fileSize`, and `uploadedAt`. Policy details and evidence are associated with the policy, while farmer identity/contact details remain separate. The application does not receive or store image bytes.

When upgrading an existing database that was initialized from the earlier `database.sql`, Hibernate adds the policy detail and upload purpose columns, but the old `stored_files` resource-type check may still allow only farmers. After backing up the database, run:

```sql
ALTER TABLE stored_files DROP CONSTRAINT IF EXISTS stored_files_resource_type_check;
ALTER TABLE stored_files ADD CONSTRAINT stored_files_resource_type_check
    CHECK (resource_type IN ('FARMER', 'POLICY'));
```

**Neon Object Storage upload is direct:** the backend returns a presigned URL; the mobile/web app sends the binary bytes directly to that URL with HTTP `PUT` and the signed `Content-Type`. Do not send base64 or multipart image bytes to Spring Boot. After the PUT, call the completion endpoint so the backend checks Neon object metadata and marks the file record uploaded. Keep the Neon bucket private; presigned URLs grant temporary access only to the individual object. For browser uploads, set bucket CORS using Neon Object Storage's `PutBucketCors` support, allowing only the app's exact web origin, `PUT`, and `Content-Type`; native Android/iOS uploads are not governed by browser CORS. The API stores only metadata and the object key. Live verification requires a configured Neon branch endpoint and scoped storage credential, an active agent, and a policy in that agent's vendor; local tests validate signing and completion behavior using a mocked Neon S3 client.

Neon Object Storage setup references: [Quickstart](https://neon.com/docs/storage/get-started), [Authentication](https://neon.com/docs/storage/authentication), [Objects and presigned URLs](https://neon.com/docs/storage/objects). Use the S3 endpoint and region returned for the Neon branch's storage state. Neon credentials are branch-scoped and the credential values are shown only once.

The temporary dummy payment flow uses `POST /api/v1/policies/{policyId}/payments/dummy-order` and then `POST /api/v1/payments/{paymentId}/simulate-success`. Simulated success marks the policy active and creates one invoice record; repeat confirmations are idempotent. The order/success endpoints return `503` when `DUMMY_PAYMENT_ENABLED=false`, which is the safe production default. This is not real payment processing: never enable it in production or use it to confirm real transactions. The invoice API currently returns invoice data; PDF generation is not included. A real gateway integration must replace the simulation and verify signed provider webhooks before activating policies.

DELETE on vendors, agents, farmers, policy plans, and policies moves records to the recycle bin by setting `deleted_at`; it does not cascade through foreign keys. Deleted rows are omitted from normal lists and cannot be used in new policies. The administrator can restore a record or permanently purge it. Purge is rejected with `409 Conflict` when modeled child records still refer to it; purge dependent records first, and database constraints also protect references such as payments and invoices. This keeps policy/payment history safe across multiple joins. Restoring a vendor does not restore separately deleted agents or records. Only pending policies can be edited; prices and GST are recalculated from the selected active plan.

Excel exports include active (non-recycled) rows only. `/api/v1/exports` creates one workbook with Vendors, Agents, Farmers, Policy plans, and Policies sheets; the singular-resource endpoint creates a workbook containing that data sheet. Exports are administrator-only, use paged database reads and streaming workbook output, and are capped at 100,000 rows per sheet. Responses disable browser caching.

## Still to integrate

The payment implementation is a demo simulator only; real gateway orders, signed webhook verification, PDF invoices, service requests/visits, and audit logs remain future work. For an existing database, `aadhaar_hash` is nullable in the JPA mapping so existing records can be migrated safely; new API registration requires it. Backfill keyed hashes from a controlled, authorized Aadhaar collection/reconciliation process, then apply a versioned migration making it `NOT NULL`. Never place Aadhaar plaintext in this schema. Also canonicalize existing user mobile values and resolve collisions before relying on canonical E.164 uniqueness behavior. Deploy versioned migrations for these changes and the newly added payment/invoice/file tables and uniqueness constraints; the supplied SQL is the fresh-schema baseline. JPA's local `update` mode is only for development, not a production migration strategy.
