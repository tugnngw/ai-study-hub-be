-- =========================================================
-- V2.2  Create account_token table
-- =========================================================
-- Purpose:
--   Dedicated table for all one-time account tokens.
--   Replaces ad-hoc token columns on account (email verify,
--   password reset, etc.).
--
--   type discriminates the purpose — EMAIL_VERIFICATION,
--   PASSWORD_RESET, etc.
-- =========================================================

CREATE TABLE account_token (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token      VARCHAR(64)  NOT NULL,
    type       VARCHAR(50)  NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at    TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Globally unique token — every token is a one-time credential.
CREATE UNIQUE INDEX idx_account_token_token ON account_token (token);

-- Composite index for findActiveByAccountAndType(account, type, now):
--   WHERE account_id = ? AND type = ? AND used_at IS NULL AND expires_at > ?
-- The leading columns (account_id, type) narrow scans to a single
-- account's token rows of one type; PostgreSQL can also use it for
-- the ORDER BY createdAt DESC LIMIT 1.
CREATE INDEX idx_account_token_account_type ON account_token (account_id, type);
