-- V20: Change storage_gb columns from INTEGER to DOUBLE PRECISION
-- to support decimal GB values (e.g. 0.5 GB = 512 MB)

ALTER TABLE payment_plan
    ALTER COLUMN storage_gb TYPE DOUBLE PRECISION USING storage_gb::DOUBLE PRECISION;

ALTER TABLE account
    ALTER COLUMN storage_gb TYPE DOUBLE PRECISION USING storage_gb::DOUBLE PRECISION;

ALTER TABLE subscriptions
    ALTER COLUMN storage_gb_granted TYPE DOUBLE PRECISION USING storage_gb_granted::DOUBLE PRECISION;
