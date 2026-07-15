-- V30: Fix FREE plan quota limits (ensure question_limit is set)
-- The V28 migration might not have updated due to case sensitivity

UPDATE payment_plan 
SET 
    flashcard_limit = 10,
    question_limit = 5,
    summary_limit = 3
WHERE name ILIKE 'free';

-- Verify the update
SELECT name, flashcard_limit, question_limit, summary_limit, duration_days 
FROM payment_plan 
WHERE name ILIKE 'free';