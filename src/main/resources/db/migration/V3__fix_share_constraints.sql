-- =========================================================
-- V3: Fix share table constraints for document support
-- =========================================================

-- 1. Remove NOT NULL constraint from folder_id (allow null for document shares)
ALTER TABLE share ALTER COLUMN folder_id DROP NOT NULL;

-- 2. Drop the unique constraint that requires both folder_id and shared_account_id
ALTER TABLE share DROP CONSTRAINT IF EXISTS share_folder_id_shared_account_id_key;

-- 3. Add separate unique constraints for folder and document shares
-- For folder shares: unique combination of folder_id and shared_account_id
CREATE UNIQUE INDEX idx_share_folder_user ON share(folder_id, shared_account_id) WHERE folder_id IS NOT NULL AND shared_account_id IS NOT NULL;

-- For document shares: unique combination of document_id and shared_account_id
CREATE UNIQUE INDEX idx_share_document_user ON share(document_id, shared_account_id) WHERE document_id IS NOT NULL AND shared_account_id IS NOT NULL;

-- 4. Ensure document_id is not null when folder_id is null and vice versa (at least one must be set)
-- This is handled at application level
