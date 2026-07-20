-- =========================================================
-- V2.1 Add email_verified column
-- =========================================================
-- Purpose:
--   Tracks whether a user has verified their email address.
--   Also widens email column (40 -> 255) for modern email support.
--   All existing users are set to TRUE to avoid locking them out.
--
-- Dependencies: V2.0__add_subscription_snapshots.sql
-- =========================================================

ALTER TABLE account
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Widen email column: 40 chars is too short for many valid emails
ALTER TABLE account
    ALTER COLUMN email TYPE VARCHAR(255);

-- Existing users: mark as verified so they aren't locked out
UPDATE account
SET email_verified = TRUE
WHERE deleted_at IS NULL;
