-- =========================================================
-- V1.4  Billing Module
-- =========================================================
-- Purpose:
--   Creates payment and subscription infrastructure:
--   - payment_plan (available subscription tiers with limits)
--   - payment (transaction records)
--   - subscriptions (active and historical subscriptions)
--
-- Dependencies:
--   V1.0  (account references for payment and subscriptions)
--
-- Notes:
--   payment_plan.duration_days = -1 means permanent/unlimited.
--   subscriptions.upgraded_to_subscription_id tracks
--   subscription upgrades via self-referencing FK.
--   Feature limits are enforced by QuotaService.
--
-- =========================================================

CREATE TABLE payment_plan (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    storage_gb        DOUBLE PRECISION,
    price             BIGINT NOT NULL,
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    duration_days     INTEGER NOT NULL DEFAULT 30,
    features          JSONB,
    is_popular        BOOLEAN NOT NULL DEFAULT FALSE,
    display_order     INTEGER NOT NULL DEFAULT 0,
    tagline           VARCHAR(500),
    ai_questions      INTEGER,
    flashcard_limit   INTEGER NOT NULL DEFAULT 0,
    question_limit    INTEGER NOT NULL DEFAULT 0,
    summary_limit     INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_name UNIQUE (name)
);

CREATE TABLE payment (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id        UUID NOT NULL,
    plan_id           UUID,
    payos_order_code  BIGINT NOT NULL,
    amount            BIGINT NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description       TEXT,
    transaction_id    VARCHAR(255),
    payment_method    VARCHAR(50),
    expired_at        TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_account FOREIGN KEY (account_id) REFERENCES account(id)      ON DELETE CASCADE,
    CONSTRAINT fk_payment_plan    FOREIGN KEY (plan_id)    REFERENCES payment_plan(id)  ON DELETE SET NULL,
    CONSTRAINT uk_payment_order   UNIQUE (payos_order_code)
);
CREATE INDEX idx_payment_account ON payment(account_id);
CREATE INDEX idx_payment_order   ON payment(payos_order_code);
CREATE INDEX idx_payment_status  ON payment(status);

CREATE TABLE subscriptions (
    id                            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id                    UUID NOT NULL,
    plan_id                       UUID NOT NULL,
    payment_transaction_id        UUID,
    status                        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date                    TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date                      TIMESTAMP WITH TIME ZONE,
    price_paid                    BIGINT NOT NULL,
    storage_gb_granted            DOUBLE PRECISION NOT NULL,
    ai_questions_granted          INTEGER NOT NULL,
    auto_renew                    BOOLEAN DEFAULT FALSE,
    cancelled_at                  TIMESTAMP WITH TIME ZONE,
    upgraded_to_subscription_id   UUID,
    created_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sub_account  FOREIGN KEY (account_id) REFERENCES account(id)       ON DELETE CASCADE,
    CONSTRAINT fk_sub_plan     FOREIGN KEY (plan_id)    REFERENCES payment_plan(id)  ON DELETE RESTRICT,
    CONSTRAINT fk_sub_payment  FOREIGN KEY (payment_transaction_id) REFERENCES payment(id),
    CONSTRAINT fk_sub_upgrade  FOREIGN KEY (upgraded_to_subscription_id) REFERENCES subscriptions(id)
);
CREATE INDEX idx_sub_account ON subscriptions(account_id);
CREATE INDEX idx_sub_status  ON subscriptions(status);
CREATE INDEX idx_sub_end_date ON subscriptions(end_date) WHERE status = 'ACTIVE';
