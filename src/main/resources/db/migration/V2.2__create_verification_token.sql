-- =========================================================
-- V2.2 Create verification_token table
-- =========================================================
-- Purpose:
--   Stores email verification tokens for account confirmation.
--   One active token per account enforced at application level.
--
-- Dependencies: V2.1__add_email_verified.sql
-- =========================================================

CREATE TABLE verification_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(36) NOT NULL,
    account_id  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE,
    version     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_verification_token_account
        FOREIGN KEY (account_id)
        REFERENCES account (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_verification_token_token UNIQUE (token)
);

CREATE INDEX idx_verification_token_account
    ON verification_token (account_id);

CREATE INDEX idx_verification_token_expires
    ON verification_token (expires_at);
