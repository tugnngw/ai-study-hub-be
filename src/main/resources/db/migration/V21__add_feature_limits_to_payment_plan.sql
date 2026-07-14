-- V21: Add feature limits to payment_plan table
-- Combined addition of flashcard, question, and summary limits with default values

ALTER TABLE payment_plan
    ADD COLUMN IF NOT EXISTS flashcard_limit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS question_limit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS summary_limit INTEGER NOT NULL DEFAULT 0;

-- Set default duration_days for existing plans that have NULL values
UPDATE payment_plan SET duration_days = 30 WHERE name = 'Pro' AND (duration_days IS NULL OR duration_days = 0);
UPDATE payment_plan SET duration_days = 30 WHERE name = 'Premium' AND (duration_days IS NULL OR duration_days = 0);
UPDATE payment_plan SET duration_days = 1 WHERE name = 'Test' AND (duration_days IS NULL OR duration_days = 0);
UPDATE payment_plan SET duration_days = 0 WHERE name = 'Free' AND (duration_days IS NULL);