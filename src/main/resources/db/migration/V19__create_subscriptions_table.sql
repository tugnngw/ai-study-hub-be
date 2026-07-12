CREATE TABLE IF NOT EXISTS subscriptions (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id                  UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    plan_id                     UUID NOT NULL REFERENCES payment_plan(id) ON DELETE RESTRICT,
    payment_transaction_id      UUID REFERENCES payment(id),

    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    start_date      TIMESTAMP WITH TIME ZONE NOT NULL,
    end_date        TIMESTAMP WITH TIME ZONE,

    price_paid              BIGINT NOT NULL,
    storage_gb_granted      INTEGER NOT NULL,
    ai_questions_granted    INTEGER NOT NULL,

    auto_renew                      BOOLEAN DEFAULT false,
    cancelled_at                    TIMESTAMP WITH TIME ZONE,
    upgraded_to_subscription_id     UUID,

    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (upgraded_to_subscription_id) REFERENCES subscriptions(id)
);

CREATE INDEX IF NOT EXISTS idx_sub_account ON subscriptions(account_id);
CREATE INDEX IF NOT EXISTS idx_sub_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_sub_end_date ON subscriptions(end_date) WHERE status = 'ACTIVE';
