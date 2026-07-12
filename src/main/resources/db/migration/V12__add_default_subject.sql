-- =========================================================
-- V12: Add defaultSubject flag to Subject
-- =========================================================
ALTER TABLE subject ADD COLUMN default_subject BOOLEAN NOT NULL DEFAULT FALSE;
