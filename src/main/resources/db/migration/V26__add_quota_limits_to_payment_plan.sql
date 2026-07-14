-- V26: Add quota limit columns to payment_plan table
-- These columns are referenced by the QuotaService for flashcard, question, and summary limits

ALTER TABLE payment_plan
    ADD COLUMN IF NOT EXISTS flashcard_limit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS question_limit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS summary_limit INTEGER NOT NULL DEFAULT 0;