-- =========================================================
-- V2: Add document sharing support
-- =========================================================

-- 1. Add document_id column to share table
ALTER TABLE share ADD COLUMN document_id UUID REFERENCES document(id) ON DELETE CASCADE;

-- 2. Add share_token column for unique share links
ALTER TABLE share ADD COLUMN share_token VARCHAR(36);

-- 3. Add revoked column for link revocation
ALTER TABLE share ADD COLUMN revoked BOOLEAN NOT NULL DEFAULT FALSE;

-- 4. Add expires_at column for time-limited sharing
ALTER TABLE share ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

-- 5. Generate share tokens for existing shares
UPDATE share SET share_token = gen_random_uuid()::text WHERE share_token IS NULL;

-- 6. Make share_token unique and not null
ALTER TABLE share ALTER COLUMN share_token SET NOT NULL;
ALTER TABLE share ADD CONSTRAINT uq_share_token UNIQUE (share_token);

-- 7. Index for document shares
CREATE INDEX idx_share_document ON share(document_id);

-- 8. Index for active share lookup
CREATE INDEX idx_share_token_active ON share(share_token, revoked, expires_at) WHERE revoked = FALSE;
