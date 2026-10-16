# JB Solar --- Project Build Specification

## 1. Project Goal

JB Solar is a solar pump installation, maintenance, payment, and service
management platform connecting JB Solar with approximately 350 vendor
companies, their agents, and farmers/customers.

### Applications

-   **Next.js** --- JB Solar Admin Web Portal
-   **React Native** --- Vendor Agent Mobile App
-   **Spring Boot** --- Central Backend API
-   **PostgreSQL** --- Transactional business database
-   **AWS S3** --- Images, documents, signatures, and PDFs
-   **Payment Gateway** --- Customer payments
-   **Docker** --- Application packaging
-   **Jenkins** --- CI/CD
-   **Prometheus + Grafana** --- Monitoring

------------------------------------------------------------------------

## 2. Main Business Workflow

``` text
JB Solar Admin
      |
      +--> Create Vendor
              |
              +--> Create Vendor Agent
                        |
                        +--> Agent logs into mobile app
                                  |
                                  +--> Register Farmer
                                  |
                                  +--> Upload Photos/Documents
                                  |
                                  +--> Select Policy Plan
                                  |
                                  +--> Initiate Payment
                                  |
                                  +--> Payment Gateway
                                  |
                                  +--> Payment Success
                                  |
                                  +--> Policy becomes ACTIVE
                                  |
                                  +--> Invoice Generated
                                  |
                                  +--> Admin sees transaction
```

Later service workflow:

``` text
Farmer
  |
  +--> Service Request
          |
          +--> Agent Assigned
                  |
                  +--> Service Visit
                          |
                          +--> Upload Before/After Photos
                          |
                          +--> Record Resolution
                          |
                          +--> Complete Service
```

------------------------------------------------------------------------

## 3. User Roles

### ADMIN

Can:

-   Manage vendors
-   Manage agents
-   Manage farmers
-   Manage pumps
-   Manage installations
-   Manage policy plans
-   View policies
-   View payments
-   View invoices
-   Manage service requests
-   View revenue
-   View reports
-   Manage users
-   View audit history

### VENDOR_AGENT

Can:

-   Login
-   Register farmers
-   Register pumps
-   Record installations
-   Upload photos/documents
-   Select policy plans
-   Initiate payments
-   View their customers
-   Create service requests
-   Update assigned service requests
-   Record service visits
-   View customer history

Agents must only access data belonging to their vendor.

------------------------------------------------------------------------

# 4. Backend Architecture

Use a **Spring Boot modular monolith** initially.

``` text
backend/
|
+-- auth/
+-- user/
+-- vendor/
+-- agent/
+-- farmer/
+-- pump/
+-- installation/
+-- policy/
+-- payment/
+-- invoice/
+-- service/
+-- file/
+-- notification/
+-- report/
+-- audit/
```

Do not start with microservices. The modular monolith can later be split
if individual modules require independent scaling.

------------------------------------------------------------------------

# 5. Database

The existing SQL provides a foundation for:

``` text
users
vendors
vendor_agents
farmers
policy_plans
policies
payments
invoices
```

For the complete system, add the following.

## Pumps

``` text
pumps
----------------
id
serial_number
model
brand
capacity
status
created_at
updated_at
```

## Installations

``` text
installations
----------------
id
farmer_id
pump_id
vendor_id
agent_id
installation_date
latitude
longitude
status
remarks
created_at
updated_at
```

## Files

Do not store large images directly in PostgreSQL.

``` text
files
----------------
id
entity_type
entity_id
file_type
storage_key
original_filename
mime_type
file_size
created_by
created_at
```

Actual file:

``` text
AWS S3
  |
  +-- image/document/PDF
```

PostgreSQL stores the file metadata and S3 key.

------------------------------------------------------------------------

# 6. Service Management

## service_requests

``` text
service_requests
----------------
id
farmer_id
pump_id
policy_id
vendor_id
assigned_agent_id
issue_type
description
priority
status
created_at
updated_at
```

Possible statuses:

``` text
OPEN
ASSIGNED
IN_PROGRESS
COMPLETED
CANCELLED
```

## service_visits

``` text
service_visits
----------------
id
service_request_id
agent_id
visit_date
remarks
resolution
latitude
longitude
created_at
```

Service photographs are stored in S3 and referenced through the `files`
table.

------------------------------------------------------------------------

# 7. Farmer Registration

React Native:

``` text
Register Farmer

Name
Mobile
Address
District
Taluka
Village

[Next]
```

API:

``` http
POST /api/v1/farmers
```

Example:

``` json
{
  "fullName": "Rajesh Patil",
  "mobile": "9876543210",
  "aadhaarNumber": "234567891234",
  "address": "Village XYZ",
  "district": "Pune",
  "taluka": "Baramati",
  "village": "ABC"
}
```

The backend should derive `created_by` and `vendor_id` from the
authenticated agent. Do not trust these values from the mobile client.
The Aadhaar number is required for identity deduplication. Store only a
keyed HMAC digest in the database, enforce a unique index on that digest,
and never return or log the Aadhaar number.

------------------------------------------------------------------------

# 8. Solar System Photos and Documents

The current backend does not create pump or solar-set inventory records.
Until the full solar system model is decided, associate agent-uploaded
photos and documents with the farmer record.

Upload directly to object storage:

``` text
React Native
      |
      +--> Request signed S3 URL
                    |
                    +--> Spring Boot
      |
      +--> Upload directly to S3
```

This avoids sending large image files through the Spring Boot server.
Use `POST /api/v1/mobile/files/upload-url` with the farmer resource type
and ID, PUT the file bytes directly to the returned private S3 URL, then
call `POST /api/v1/mobile/files/{fileId}/complete`. Only metadata and
the private object key are stored in PostgreSQL.

------------------------------------------------------------------------

# 9. Policy Workflow

Admin creates policy plans.

Example:

``` text
Basic Plan
12 months
Price: configured by admin
GST: configured by admin

Premium Plan
24 months
Price: configured by admin
GST: configured by admin
```

Agent sees active plans in the mobile app.

The backend calculates:

``` text
Base Amount
+
GST
=
Total Amount
```

The backend must retrieve the official plan price from the database
rather than trusting a price supplied by the mobile application.

------------------------------------------------------------------------

# 10. Payment Workflow

Payment is a critical transaction.

``` text
Policy PENDING
      |
      +--> Create Payment Order
              |
              +--> Payment Gateway
                      |
                      +--> Customer Payment
                              |
                              +--> Gateway Webhook
                                      |
                                      +--> Backend Verification
                                              |
                                              +--> Payment SUCCESS
                                                      |
                                                      +--> Policy ACTIVE
                                                      |
                                                      +--> Invoice Generated
```

Payment statuses:

``` text
PENDING
SUCCESS
FAILED
REFUNDED
```

The backend currently includes a demo-only payment simulator controlled by
`DUMMY_PAYMENT_ENABLED`. A simulated success activates a pending policy and
creates one invoice record. Never enable this simulator in production.
Replace it later with a real provider order and signed webhook verification.

------------------------------------------------------------------------

# 11. Invoice Workflow

After successful payment:

``` text
Payment SUCCESS
      |
      +--> Generate Invoice Number
      |
      +--> Create Invoice
      |
      +--> Generate PDF
      |
      +--> Upload PDF to S3
      |
      +--> Store S3 key in PostgreSQL
```

Admin can:

-   View invoice
-   Download invoice
-   Send invoice to customer

------------------------------------------------------------------------

# 12. Admin Portal

Next.js pages:

``` text
Dashboard
|
+-- Vendors
|   +-- List
|   +-- Create
|   +-- Edit
|   +-- Details
|
+-- Agents
|
+-- Farmers
|
+-- Pumps
|
+-- Installations
|
+-- Policy Plans
|
+-- Policies
|
+-- Payments
|
+-- Invoices
|
+-- Service Requests
|
+-- Service History
|
+-- Reports
|
+-- Users
|
+-- Audit Logs
```

Dashboard metrics:

``` text
Total Vendors
Total Agents
Total Farmers
Total Pumps
Active Policies
Total Revenue
Pending Payments
Pending Services
Completed Services
```

------------------------------------------------------------------------

# 13. React Native Agent Application

Main flow:

``` text
Login
  |
  +--> Dashboard
          |
          +--> Customers
          |
          +--> Register Farmer
          |
          +--> Add Pump
          |
          +--> Installation
          |
          +--> Select Policy
          |
          +--> Payment
          |
          +--> Invoice/Confirmation
```

Main navigation:

``` text
Home
Customers
Services
Notifications
Profile
```

Customer details:

``` text
Farmer
|
+-- Personal Information
+-- Pump
+-- Policy
+-- Payments
+-- Invoice
+-- Service History
```

------------------------------------------------------------------------

# 14. S3 Storage Structure

Recommended structure:

``` text
jbsolar/
|
+-- farmers/
|     +-- {farmer-id}/
|           +-- profile.webp
|
+-- pumps/
|     +-- {pump-id}/
|           +-- pump.webp
|           +-- serial-number.webp
|
+-- installations/
|     +-- {installation-id}/
|           +-- installation-1.webp
|           +-- installation-2.webp
|
+-- services/
|     +-- {service-id}/
|           +-- before.webp
|           +-- after.webp
|
+-- invoices/
      +-- {invoice-id}/
            +-- invoice.pdf
```

Use image resizing/compression for normal photos. Retain originals where
required for audit, warranty, or legal evidence.

------------------------------------------------------------------------

# 15. API Structure

Use API versioning from the beginning.

``` text
/api/v1/auth
/api/v1/vendors
/api/v1/agents
/api/v1/farmers
/api/v1/policy-plans
/api/v1/policies
/api/v1/payments
/api/v1/invoices
/api/v1/services
/api/v1/files
/api/v1/reports
```

Examples:

``` http
POST /api/v1/auth/login          (admin website only)
POST /api/v1/auth/agent/login    (vendor-agent mobile app only)

GET /api/v1/farmers
POST /api/v1/farmers
GET /api/v1/farmers/{id}

GET /api/v1/policy-plans
POST /api/v1/policies

POST /api/v1/policies/{policyId}/payments/dummy-order
POST /api/v1/payments/{paymentId}/simulate-success
GET  /api/v1/policies/{policyId}/invoice

GET /api/v1/invoices/{id}

POST /api/v1/services
GET /api/v1/services
PUT /api/v1/services/{id}
```

Images and documents do not pass through the API server. The app requests
`POST /api/v1/mobile/files/upload-url`, uploads the file bytes directly to
the returned private S3 URL using HTTP PUT, then calls
`POST /api/v1/mobile/files/{fileId}/complete`. PostgreSQL stores metadata
and the object key only.

------------------------------------------------------------------------

# 16. Security

Use Spring Security.

``` text
Login
  |
  +--> JWT Access Token
          |
          +--> Role
          |
          +--> Authorization
          |
          +--> Vendor Data Isolation
```

Example:

``` text
Agent A
Vendor = ABC
        |
        +--> Can access ABC customers
        |
        X--> Cannot access XYZ customers
```

Important security rules:

-   Never trust vendor ID from the client.
-   Never trust agent ID from the client.
-   Validate ownership on every protected resource.
-   Validate uploaded files.
-   Limit file size and file types.
-   Keep S3 objects private.
-   Use signed URLs for controlled access.
-   Store secrets in environment variables/secret management.
-   Use HTTPS in production.
-   Keep Aadhaar values out of logs and persist only a keyed HMAC digest.
-   Set a stable `IDENTITY_HASH_KEY` and private Neon Object Storage bucket/credentials in production (`NEON_STORAGE_ENDPOINT`, `NEON_STORAGE_REGION`, `NEON_STORAGE_BUCKET`, `NEON_STORAGE_ACCESS_KEY_ID`, and `NEON_STORAGE_SECRET_ACCESS_KEY`).

------------------------------------------------------------------------

# 17. Deployment Architecture

``` text
                    INTERNET
                       |
            +----------+----------+
            |                     |
            v                     v
     Next.js Admin          React Native App
     admin.jbsolar.com             |
            |                      |
            +----------+-----------+
                       |
                       v
                api.jbsolar.com
                       |
                     Nginx
                       |
                Spring Boot
                  Docker
                       |
          +------------+------------+
          |            |             |
          v            v             v
     PostgreSQL       S3       Payment Gateway
```

Monitoring:

``` text
Spring Boot
     |
     v
Prometheus
     |
     v
Grafana
```

------------------------------------------------------------------------

# 18. CI/CD

Backend:

``` text
Developer
    |
    +--> git push
            |
            v
          GitHub
            |
            v
          Jenkins
            |
            +--> Unit Tests
            |
            +--> Integration Tests
            |
            +--> Build JAR
            |
            +--> Build Docker Image
            |
            +--> Push Image
            |
            +--> Deploy
            |
            +--> Health Check
```

Frontend:

``` text
GitHub
   |
   v
Vercel
   |
   v
Next.js Build
   |
   v
Production
```

------------------------------------------------------------------------

# 19. Development Phases

## Phase 1 --- Backend Foundation

-   Spring Boot project
-   PostgreSQL
-   Database migrations
-   Spring Security
-   JWT
-   Validation
-   Global exception handling
-   Logging
-   API response structure

## Phase 2 --- Authentication

-   Admin login
-   Agent login
-   Role authorization
-   Vendor isolation
-   Refresh token/session strategy

## Phase 3 --- Vendor and Agent

-   Vendor CRUD
-   Agent CRUD
-   Agent → Vendor relationship

## Phase 4 --- Farmer

-   Farmer registration
-   Farmer listing
-   Farmer details
-   Search/filter

## Phase 5 --- Pump and Installation

-   Pump CRUD
-   Installation
-   Photos
-   S3 integration
-   Image compression

## Phase 6 --- Policy

-   Policy plans
-   Policy creation
-   Policy activation
-   Policy expiry

## Phase 7 --- Payment

-   Payment order
-   Payment gateway
-   Webhook
-   Payment verification
-   Success/failure/refund handling

## Phase 8 --- Invoice

-   Invoice generation
-   PDF generation
-   S3 storage
-   Download
-   Invoice history

## Phase 9 --- Service

-   Service request
-   Agent assignment
-   Service visit
-   Before/after photos
-   Resolution
-   Service history

## Phase 10 --- Admin Portal

-   Dashboard
-   Vendors
-   Agents
-   Farmers
-   Pumps
-   Policies
-   Payments
-   Invoices
-   Services
-   Reports

## Phase 11 --- Production Deployment

``` text
Docker
  |
AWS EC2
  |
Nginx
  |
HTTPS
  |
Domain
```

Along with:

-   Managed PostgreSQL
-   S3
-   Backup
-   Security groups
-   Secrets
-   Logging
-   Health checks

## Phase 12 --- DevOps

``` text
GitHub
   |
Jenkins
   |
Automated Tests
   |
Docker
   |
Deployment
   |
Prometheus
   |
Grafana
```

------------------------------------------------------------------------

# 20. Recommended Build Order

Do not build the complete application simultaneously.

Build one complete business flow first:

``` text
Login
  ↓
Vendor
  ↓
Agent
  ↓
Farmer
  ↓
Pump
  ↓
Installation
  ↓
Policy
  ↓
Payment
  ↓
Invoice
```

Once this end-to-end workflow works, add:

``` text
Service Management
       ↓
Notifications
       ↓
Reports
       ↓
Advanced Dashboard
       ↓
Offline Mobile Sync
       ↓
Advanced Monitoring
```

This keeps the project aligned with an incremental/agile development
approach while producing a usable system at every major stage.
