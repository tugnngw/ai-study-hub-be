-- =========================================================
-- V1.8  Add tier column to payment_plan for upgrade/downgrade
-- =========================================================
-- Purpose:
--   Adds a tier (integer) column to payment_plan so that
--   plans can be compared hierarchically. Higher tier = more
--   features. Used to prevent downgrades when a user has an
--   active subscription.
--
-- Convention:
--   tier=0: Free
--   tier=1: Basic
--   tier=2: Pro
--   tier=3: Premium
--   (higher values for future plans)
-- =========================================================

ALTER TABLE payment_plan
    ADD COLUMN IF NOT EXISTS tier INTEGER NOT NULL DEFAULT 0;

UPDATE payment_plan SET tier = 0 WHERE LOWER(name) = 'free';
UPDATE payment_plan SET tier = 1 WHERE LOWER(name) = 'basic';
UPDATE payment_plan SET tier = 2 WHERE LOWER(name) = 'pro';
UPDATE payment_plan SET tier = 3 WHERE LOWER(name) = 'premium';
