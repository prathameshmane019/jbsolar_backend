

CREATE SEQUENCE IF NOT EXISTS vendor_code_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS agent_code_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS plan_code_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS farmer_code_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS policy_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS payment_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;

CREATE SEQUENCE IF NOT EXISTS invoice_number_seq
    START WITH 1
    INCREMENT BY 1
    NO CYCLE;
-- Auto-update updated_at
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------
CREATE TABLE users (
                       id            UUID PRIMARY KEY,
                       mobile        VARCHAR(20)  NOT NULL UNIQUE,
                       password_hash TEXT         NOT NULL,
                       role          VARCHAR(255) NOT NULL CHECK (role IN ('ADMIN', 'VENDOR_AGENT')),
                       status        VARCHAR(255) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
                       last_login_at TIMESTAMP(0) NULL,
                       created_at    TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at    TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS vendor_code_seq
    START WITH 1
    INCREMENT BY 1;

CREATE TABLE vendors (
                         id             UUID PRIMARY KEY,
                         vendor_code    VARCHAR(30) NOT NULL UNIQUE
                                                              DEFAULT 'VND-' || lpad(nextval('vendor_code_seq')::text, 4, '0'),
                         name           VARCHAR(150) NOT NULL,
                         contact_person VARCHAR(100),
                         mobile         VARCHAR(20),
                         email          VARCHAR(150),
                         status         VARCHAR(255) NOT NULL DEFAULT 'ACTIVE'
                             CHECK (status IN ('ACTIVE', 'INACTIVE')),
                         created_at     TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at     TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deleted_at     TIMESTAMP(0)
);
-- ---------------------------------------------------------------------
CREATE TABLE vendor_agents (
                               id         UUID PRIMARY KEY,
                               agent_code VARCHAR(30)  NOT NULL UNIQUE
                                                                DEFAULT 'AGT-' || lpad(nextval('agent_code_seq')::text, 4, '0'),
                               user_id    UUID         NOT NULL UNIQUE REFERENCES users(id),
                               vendor_id  UUID         NOT NULL REFERENCES vendors(id),
                               full_name  VARCHAR(150) NOT NULL,
                               status     VARCHAR(255) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
                               created_at TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               deleted_at TIMESTAMP(0) NULL
);
CREATE INDEX vendor_agents_vendor_id_index  ON vendor_agents (vendor_id);
CREATE INDEX vendor_agents_deleted_at_index ON vendor_agents (deleted_at);

-- ---------------------------------------------------------------------
-- Aadhaar: stored reversibly so it can be shown/used when needed.
--   aadhaar_encrypted : encrypt in the app (AES-256-GCM, key in env/KMS, NOT in DB)
--   aadhaar_last4     : plain last 4 digits for display/search (e.g. XXXX XXXX 1234)
-- No unique constraint on the encrypted value (encryption is randomized).
-- ---------------------------------------------------------------------
CREATE TABLE farmers (
                         id                UUID PRIMARY KEY,
                         farmer_code     VARCHAR(50)  NOT NULL UNIQUE
                                                                 DEFAULT 'FM-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('farmer_code_seq')::text, 6, '0'),
                         full_name         VARCHAR(150) NOT NULL,
                         mobile            VARCHAR(20)  NOT NULL,
                         aadhaar_hash  VARCHAR(64)  NULL,
                         address           TEXT         NULL,
                         district          VARCHAR(100) NULL,
                         taluka            VARCHAR(100) NULL,
                         village           VARCHAR(100) NULL,
                         created_by        UUID         NULL REFERENCES vendor_agents(id),
                         created_at        TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at        TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deleted_at        TIMESTAMP(0) NULL
);
CREATE INDEX farmers_mobile_index              ON farmers (mobile);
CREATE INDEX farmers_created_by_deleted_at_idx ON farmers (created_by, deleted_at);
CREATE INDEX farmers_deleted_at_index          ON farmers (deleted_at);

-- ---------------------------------------------------------------------
CREATE TABLE stored_files (
                              id                UUID PRIMARY KEY,
                              resource_type     VARCHAR(30)  NOT NULL CHECK (resource_type IN ('FARMER', 'POLICY')),
                              resource_id       UUID         NOT NULL,
                              purpose           VARCHAR(30)  NULL CHECK (purpose IN ('FARMER_DOCUMENT', 'CUSTOMER_SIGNATURE', 'PUMP_SET_IMAGE')),
                              storage_key       VARCHAR(500) NOT NULL UNIQUE,
                              original_filename VARCHAR(255) NOT NULL,
                              content_type      VARCHAR(100) NOT NULL,
                              file_size         BIGINT       NOT NULL CHECK (file_size BETWEEN 1 AND 10485760),
                              created_by        UUID         NOT NULL REFERENCES vendor_agents(id),
                              created_at        TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              uploaded_at       TIMESTAMP(0) NULL
);
CREATE INDEX stored_files_resource_index ON stored_files (resource_type, resource_id, created_by, uploaded_at);

-- ---------------------------------------------------------------------
CREATE TABLE policy_plans (
                              id                   UUID PRIMARY KEY,
                              plan_code            VARCHAR(30)  NOT NULL UNIQUE
                                                                         DEFAULT 'PLN-' || lpad(nextval('plan_code_seq')::text, 4, '0'),
                              name                 VARCHAR(150) NOT NULL,
                              description          TEXT NULL,
                              duration_months      INTEGER      NOT NULL CHECK (duration_months > 0),
                              price                NUMERIC(12,2) NOT NULL CHECK (price >= 0),
                              gst_percentage       NUMERIC(5,2)  NOT NULL CHECK (gst_percentage BETWEEN 0 AND 100),
                              terms_and_conditions TEXT NULL,
                              status               VARCHAR(255) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
                              created_at           TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              updated_at           TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              deleted_at           TIMESTAMP(0) NULL
);
CREATE INDEX policy_plans_deleted_at_index ON policy_plans (deleted_at);

-- ---------------------------------------------------------------------
CREATE TABLE policies (
                          id                UUID PRIMARY KEY,
                          policy_number     VARCHAR(50)  NOT NULL UNIQUE
                                                                  DEFAULT 'PLC-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('policy_number_seq')::text, 6, '0'),
                          farmer_id         UUID NOT NULL REFERENCES farmers(id),
                          policy_plan_id    UUID NOT NULL REFERENCES policy_plans(id),
                          vendor_id         UUID NOT NULL REFERENCES vendors(id),
                          start_date        DATE NOT NULL,
                          end_date          DATE NOT NULL,
                          amount            NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
                          gst_amount        NUMERIC(12,2) NOT NULL CHECK (gst_amount >= 0),
                          total_amount      NUMERIC(12,2) NOT NULL,
                          pump_power_hp     NUMERIC(8,2) NULL,
                          motor_head_meters NUMERIC(8,2) NULL,
                          status            VARCHAR(255) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
                          purchased_at      TIMESTAMP(0) NULL,
                          created_by        UUID NULL REFERENCES vendor_agents(id),
                          created_at        TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at        TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          deleted_at        TIMESTAMP(0) NULL,
                          CONSTRAINT policies_dates_chk CHECK (end_date > start_date),
                          CONSTRAINT policies_total_chk CHECK (total_amount = amount + gst_amount)
);
CREATE INDEX policies_farmer_id_index      ON policies (farmer_id);
CREATE INDEX policies_policy_plan_id_index ON policies (policy_plan_id);
CREATE INDEX policies_vendor_status_index  ON policies (vendor_id, status);
CREATE INDEX policies_created_by_index     ON policies (created_by);
CREATE INDEX policies_deleted_at_index     ON policies (deleted_at);

-- ---------------------------------------------------------------------
CREATE TABLE payments (
                          id                 UUID PRIMARY KEY,
                          payment_number     VARCHAR(50)  NOT NULL UNIQUE
                                                                   DEFAULT 'PAY-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('payment_number_seq')::text, 6, '0'),
                          policy_id          UUID NOT NULL REFERENCES policies(id),
                          gateway            VARCHAR(50)  NOT NULL,
                          gateway_order_id   VARCHAR(150) NOT NULL,
                          gateway_payment_id VARCHAR(150) NULL,
                          amount             NUMERIC(12,2) NOT NULL CHECK (amount > 0),
                          payment_method     VARCHAR(50)  NULL,
                          status             VARCHAR(255) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
                          paid_at            TIMESTAMP(0) NULL,
                          created_at         TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at         TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX payments_policy_status_index ON payments (policy_id, status);
CREATE UNIQUE INDEX payments_gateway_order_unique ON payments (gateway, gateway_order_id);
CREATE UNIQUE INDEX payments_gateway_payment_unique ON payments (gateway, gateway_payment_id)
    WHERE gateway_payment_id IS NOT NULL;

-- ---------------------------------------------------------------------
CREATE TABLE invoices (
                          id             UUID PRIMARY KEY,
                          invoice_number VARCHAR(50)  NOT NULL UNIQUE
                                                               DEFAULT 'INV-' || to_char(CURRENT_DATE, 'YYYY') || '-' || lpad(nextval('invoice_number_seq')::text, 6, '0'),
                          policy_id      UUID NOT NULL UNIQUE REFERENCES policies(id),
                          payment_id     UUID NOT NULL UNIQUE REFERENCES payments(id),
                          invoice_date   DATE NOT NULL,
                          amount         NUMERIC(12,2) NOT NULL,
                          gst_amount     NUMERIC(12,2) NOT NULL,
                          total_amount   NUMERIC(12,2) NOT NULL,
                          created_at     TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT invoices_total_chk CHECK (total_amount = amount + gst_amount)
);

-- ---------------------------------------------------------------------
-- updated_at triggers
-- ---------------------------------------------------------------------
DO $$
DECLARE t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY['users','vendors','vendor_agents','farmers','policy_plans','policies','payments']
    LOOP
        EXECUTE format(
            'CREATE TRIGGER %I BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION set_updated_at()',
            t || '_set_updated_at', t);
END LOOP;
END $$;