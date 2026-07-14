-- =========================================================
-- V14: Add description column to folder table
-- =========================================================
ALTER TABLE folder ADD COLUMN IF NOT EXISTS description TEXT;
