-- =========================================================
-- V3.2  Storage accounting moves to Account (used_storage_bytes)
-- =========================================================
-- Purpose:
--   Backend becomes the single source of truth for storage usage:
--     account.used_storage_bytes  = bytes actually used
--     subscriptions.max_storage_gb = entitlement snapshot copied from plan
--                                   (never read plan directly when checking)
--   Storage counter chỉ thay đổi ở 2 sự kiện:
--     upload thành công      → used += fileSize
--     permanent delete       → used -= fileSize
--   Không filter status/deleted_at khi backfill — mọi document đều được tính.
-- =========================================================

-- 1) Account: replace storage_gb (limit) with used_storage_bytes (usage)
ALTER TABLE account
    DROP COLUMN IF EXISTS storage_gb;
ALTER TABLE account
    ADD COLUMN used_storage_bytes BIGINT NOT NULL DEFAULT 0;
DROP INDEX IF EXISTS idx_account_storage;

-- 2) Backfill usage: SUM(file_size) theo owner, KHÔNG filter status/deleted_at
UPDATE account a
SET used_storage_bytes = COALESCE((
    SELECT SUM(d.file_size)
    FROM document d
    WHERE d.owner_id = a.id
), 0)
WHERE a.deleted_at IS NULL;

-- 3) Subscription: entitlement snapshot column for storage limit
ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS max_storage_gb DOUBLE PRECISION NOT NULL DEFAULT 1;

-- 4) Backfill subscriptions.max_storage_gb from plan (old behavior)
UPDATE subscriptions s
SET max_storage_gb = COALESCE(p.storage_gb, 1)
FROM payment_plan p
WHERE s.plan_id = p.id;

-- 5) Index for per-owner usage lookups
CREATE INDEX IF NOT EXISTS idx_document_owner_live
    ON document(owner_id) WHERE deleted_at IS NULL;
