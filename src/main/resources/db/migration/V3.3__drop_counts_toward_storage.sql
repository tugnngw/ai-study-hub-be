-- =========================================================
-- V3.3  Drop counts_toward_storage (storage counter simplified)
-- =========================================================
-- Storage chỉ thay đổi ở 2 sự kiện: upload (+fileSize) và permanent delete
-- (-fileSize). Boolean counts_toward_storage là cache của status — redundant,
-- bỏ. Backfill lại used_storage_bytes theo SUM(file_size) không filter.
-- =========================================================

ALTER TABLE document
    DROP COLUMN IF EXISTS counts_toward_storage;

UPDATE account a
SET used_storage_bytes = COALESCE((
    SELECT SUM(d.file_size)
    FROM document d
    WHERE d.owner_id = a.id
), 0)
WHERE a.deleted_at IS NULL;
