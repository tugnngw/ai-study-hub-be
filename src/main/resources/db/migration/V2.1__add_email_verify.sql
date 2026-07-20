-- =========================================================
-- V2.1  Add email verification columns to account
-- =========================================================
-- Purpose:
--   Store email verification state directly on account row
--   instead of a separate table.  A null verification_token
--   means "no pending verification".  A non-null verified_at
--   means this token has been consumed.
--
--   At most one active token per account (new token replaces
--   the old one atomically via UPDATE).
-- =========================================================

ALTER TABLE account ADD COLUMN email_verified              BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE account ADD COLUMN verification_token          VARCHAR(36);
ALTER TABLE account ADD COLUMN verification_token_expires_at    TIMESTAMP WITH TIME ZONE;
ALTER TABLE account ADD COLUMN verification_token_verified_at   TIMESTAMP WITH TIME ZONE;

CREATE UNIQUE INDEX idx_account_verification_token
    ON account(verification_token) WHERE verification_token IS NOT NULL;
