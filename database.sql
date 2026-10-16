CREATE TABLE "users"(
    "id" UUID NOT NULL,
    "mobile" VARCHAR(20) NOT NULL,
    "password_hash" TEXT NOT NULL,
    "role" VARCHAR(255) CHECK
        ("role" IN('ADMIN', 'VENDOR_AGENT')) NOT NULL,
        "status" VARCHAR(255)
    CHECK
        (
            "status" IN('ACTIVE', 'INACTIVE', 'BLOCKED')
        ) NOT NULL DEFAULT 'ACTIVE',
        "last_login_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL,
        "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE
    "users" ADD PRIMARY KEY("id");
ALTER TABLE
    "users" ADD CONSTRAINT "users_mobile_unique" UNIQUE("mobile");
CREATE TABLE "vendors"(
    "id" UUID NOT NULL,
    "name" VARCHAR(150) NOT NULL,
    "contact_person" VARCHAR(100) NULL,
    "mobile" VARCHAR(20) NULL,
    "email" VARCHAR(150) NULL,
    "status" VARCHAR(255) CHECK
        ("status" IN('ACTIVE', 'INACTIVE')) NOT NULL DEFAULT 'ACTIVE',
        "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "deleted_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL
);
ALTER TABLE
    "vendors" ADD PRIMARY KEY("id");
CREATE INDEX "vendors_deleted_at_index" ON
    "vendors"("deleted_at");
CREATE TABLE "vendor_agents"(
    "id" UUID NOT NULL,
    "user_id" UUID NOT NULL,
    "vendor_id" UUID NOT NULL,
    "full_name" VARCHAR(150) NOT NULL,
    "status" VARCHAR(255) CHECK
        ("status" IN('ACTIVE', 'INACTIVE')) NOT NULL DEFAULT 'ACTIVE',
        "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "deleted_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL
);
ALTER TABLE
    "vendor_agents" ADD PRIMARY KEY("id");
ALTER TABLE
    "vendor_agents" ADD CONSTRAINT "vendor_agents_user_id_unique" UNIQUE("user_id");
CREATE INDEX "vendor_agents_vendor_id_index" ON
    "vendor_agents"("vendor_id");
CREATE INDEX "vendor_agents_deleted_at_index" ON
    "vendor_agents"("deleted_at");
CREATE TABLE "farmers"(
    "id" UUID NOT NULL,
    "customer_code" VARCHAR(50) NOT NULL,
    "full_name" VARCHAR(150) NOT NULL,
    "mobile" VARCHAR(20) NOT NULL,
    "aadhaar_hash" VARCHAR(64) NULL,
    "address" TEXT NULL,
    "district" VARCHAR(100) NULL,
    "taluka" VARCHAR(100) NULL,
    "village" VARCHAR(100) NULL,
    "created_by" UUID NULL,
    "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deleted_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL
);
ALTER TABLE
    "farmers" ADD PRIMARY KEY("id");
ALTER TABLE
    "farmers" ADD CONSTRAINT "farmers_customer_code_unique" UNIQUE("customer_code");
CREATE INDEX "farmers_mobile_index" ON
    "farmers"("mobile");
CREATE INDEX "farmers_created_by_index" ON
    "farmers"("created_by");
CREATE INDEX "farmers_deleted_at_index" ON
    "farmers"("deleted_at");
CREATE UNIQUE INDEX "farmers_aadhaar_hash_unique" ON
    "farmers"("aadhaar_hash");
CREATE INDEX "farmers_created_by_deleted_at_index" ON
    "farmers"("created_by", "deleted_at");
CREATE TABLE "stored_files"(
    "id" UUID NOT NULL,
    "resource_type" VARCHAR(30) NOT NULL CHECK ("resource_type" IN('FARMER', 'POLICY')),
    "resource_id" UUID NOT NULL,
    "purpose" VARCHAR(30) NULL CHECK ("purpose" IN('FARMER_DOCUMENT', 'CUSTOMER_SIGNATURE', 'PUMP_SET_IMAGE')),
    "storage_key" VARCHAR(500) NOT NULL,
    "original_filename" VARCHAR(255) NOT NULL,
    "content_type" VARCHAR(100) NOT NULL,
    "file_size" BIGINT NOT NULL CHECK ("file_size" BETWEEN 1 AND 10485760),
    "created_by" UUID NOT NULL,
    "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "uploaded_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL
);
ALTER TABLE "stored_files" ADD PRIMARY KEY("id");
ALTER TABLE "stored_files" ADD CONSTRAINT "stored_files_storage_key_unique" UNIQUE("storage_key");
CREATE INDEX "stored_files_resource_index" ON
    "stored_files"("resource_type", "resource_id", "created_by", "uploaded_at");
CREATE TABLE "policy_plans"(
    "id" UUID NOT NULL,
    "name" VARCHAR(150) NOT NULL,
    "description" TEXT NULL,
    "duration_months" INTEGER NOT NULL,
    "price" DECIMAL(12, 2) NOT NULL,
    "gst_percentage" DECIMAL(5, 2) NOT NULL,
    "terms_and_conditions" TEXT NULL,
    "status" VARCHAR(255) CHECK
        ("status" IN('ACTIVE', 'INACTIVE')) NOT NULL DEFAULT 'ACTIVE',
        "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "deleted_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL
);
ALTER TABLE
    "policy_plans" ADD PRIMARY KEY("id");
CREATE INDEX "policy_plans_deleted_at_index" ON
    "policy_plans"("deleted_at");
CREATE TABLE "policies"(
    "id" UUID NOT NULL,
    "policy_number" VARCHAR(50) NOT NULL,
    "farmer_id" UUID NOT NULL,
    "policy_plan_id" UUID NOT NULL,
    "start_date" DATE NOT NULL,
    "end_date" DATE NOT NULL,
    "amount" DECIMAL(12, 2) NOT NULL,
    "gst_amount" DECIMAL(12, 2) NOT NULL,
    "total_amount" DECIMAL(12, 2) NOT NULL,
    "pump_power_hp" NUMERIC(8, 2) NULL,
    "motor_head_meters" NUMERIC(8, 2) NULL,
    "status" VARCHAR(255) CHECK
        (
            "status" IN(
                'PENDING',
                'ACTIVE',
                'EXPIRED',
                'CANCELLED'
            )
        ) NOT NULL DEFAULT 'PENDING',
        "purchased_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL,
        "created_by" UUID NULL,
        "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "vendor_id" UUID NOT NULL,
        "deleted_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL
);
ALTER TABLE
    "policies" ADD PRIMARY KEY("id");
ALTER TABLE
    "policies" ADD CONSTRAINT "policies_policy_number_unique" UNIQUE("policy_number");
CREATE INDEX "policies_farmer_id_index" ON
    "policies"("farmer_id");
CREATE INDEX "policies_policy_plan_id_index" ON
    "policies"("policy_plan_id");
CREATE INDEX "policies_status_index" ON
    "policies"("status");
CREATE INDEX "policies_created_by_index" ON
    "policies"("created_by");
CREATE INDEX "policies_deleted_at_index" ON
    "policies"("deleted_at");
CREATE TABLE "payments"(
    "id" UUID NOT NULL,
    "policy_id" UUID NOT NULL,
    "payment_number" VARCHAR(50) NOT NULL,
    "gateway" VARCHAR(50) NOT NULL,
    "gateway_order_id" VARCHAR(150) NOT NULL,
    "gateway_payment_id" VARCHAR(150) NULL,
    "amount" DECIMAL(12, 2) NOT NULL,
    "payment_method" VARCHAR(50) NULL,
    "status" VARCHAR(255) CHECK
        (
            "status" IN(
                'PENDING',
                'SUCCESS',
                'FAILED',
                'REFUNDED'
            )
        ) NOT NULL DEFAULT 'PENDING',
        "paid_at" TIMESTAMP(0) WITHOUT TIME ZONE NULL,
        "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
        "updated_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE
    "payments" ADD PRIMARY KEY("id");
CREATE INDEX "payments_policy_id_index" ON
    "payments"("policy_id");
ALTER TABLE
    "payments" ADD CONSTRAINT "payments_payment_number_unique" UNIQUE("payment_number");
CREATE INDEX "payments_status_index" ON
    "payments"("status");
CREATE TABLE "invoices"(
    "id" UUID NOT NULL,
    "invoice_number" VARCHAR(50) NOT NULL,
    "policy_id" UUID NOT NULL,
    "payment_id" UUID NOT NULL,
    "invoice_date" DATE NOT NULL,
    "amount" DECIMAL(12, 2) NOT NULL,
    "gst_amount" DECIMAL(12, 2) NOT NULL,
    "total_amount" DECIMAL(12, 2) NOT NULL,
    "created_at" TIMESTAMP(0) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE
    "invoices" ADD PRIMARY KEY("id");
ALTER TABLE
    "invoices" ADD CONSTRAINT "invoices_invoice_number_unique" UNIQUE("invoice_number");
CREATE INDEX "invoices_policy_id_index" ON
    "invoices"("policy_id");
CREATE INDEX "invoices_payment_id_index" ON
    "invoices"("payment_id");
ALTER TABLE "invoices" ADD CONSTRAINT "invoices_policy_id_unique" UNIQUE("policy_id");
ALTER TABLE "invoices" ADD CONSTRAINT "invoices_payment_id_unique" UNIQUE("payment_id");
ALTER TABLE
    "policies" ADD CONSTRAINT "policies_policy_plan_id_foreign" FOREIGN KEY("policy_plan_id") REFERENCES "policy_plans"("id");
ALTER TABLE
    "vendor_agents" ADD CONSTRAINT "vendor_agents_vendor_id_foreign" FOREIGN KEY("vendor_id") REFERENCES "vendors"("id");
ALTER TABLE
    "invoices" ADD CONSTRAINT "invoices_policy_id_foreign" FOREIGN KEY("policy_id") REFERENCES "policies"("id");
ALTER TABLE
    "policies" ADD CONSTRAINT "policies_farmer_id_foreign" FOREIGN KEY("farmer_id") REFERENCES "farmers"("id");
ALTER TABLE
    "policies" ADD CONSTRAINT "policies_vendor_id_foreign" FOREIGN KEY("vendor_id") REFERENCES "vendors"("id");
ALTER TABLE
    "vendor_agents" ADD CONSTRAINT "vendor_agents_user_id_foreign" FOREIGN KEY("user_id") REFERENCES "users"("id");
ALTER TABLE
    "invoices" ADD CONSTRAINT "invoices_payment_id_foreign" FOREIGN KEY("payment_id") REFERENCES "payments"("id");
ALTER TABLE
    "payments" ADD CONSTRAINT "payments_policy_id_foreign" FOREIGN KEY("policy_id") REFERENCES "policies"("id");
ALTER TABLE
    "policies" ADD CONSTRAINT "policies_created_by_foreign" FOREIGN KEY("created_by") REFERENCES "vendor_agents"("id");
ALTER TABLE
    "farmers" ADD CONSTRAINT "farmers_created_by_foreign" FOREIGN KEY("created_by") REFERENCES "vendor_agents"("id");
ALTER TABLE "stored_files" ADD CONSTRAINT "stored_files_created_by_foreign" FOREIGN KEY("created_by") REFERENCES "vendor_agents"("id");