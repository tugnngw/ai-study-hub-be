-- =========================================================
-- V2.3 Create password_reset_token table
-- =========================================================
-- Purpose:
--   Stores password reset tokens for the forgot-password flow.
--   One active token per account enforced at application level.
--
-- Dependencies: V2.2__create_verification_token.sql
-- =========================================================

CREATE TABLE password_reset_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(36) NOT NULL,
    account_id  UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at     TIMESTAMP WITH TIME ZONE,
    version     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_password_reset_token_account
        FOREIGN KEY (account_id)
        REFERENCES account (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_password_reset_token_token UNIQUE (token)
);

CREATE INDEX idx_password_reset_token_account
    ON password_reset_token (account_id);

CREATE INDEX idx_password_reset_token_expires
    ON password_reset_token (expires_at);
