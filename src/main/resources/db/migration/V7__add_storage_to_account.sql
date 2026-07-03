-- =========================================================
-- V7: Add storage tracking to account
-- =========================================================

-- Add storage_gb column to account (default 1GB for FREE plan)
ALTER TABLE account ADD COLUMN IF NOT EXISTS storage_gb INTEGER DEFAULT 1;

-- Update existing FREE accounts to have 1GB
UPDATE account SET storage_gb = 1 WHERE plan = 'FREE' AND storage_gb IS NULL;

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_account_storage ON account(storage_gb);
