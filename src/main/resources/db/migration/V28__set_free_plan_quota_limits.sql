-- V28: Set quota limits for Free plan
-- The Free plan exists but has quota limits set to 0 by default
-- This migration sets reasonable limits for free users

UPDATE payment_plan 
SET 
    flashcard_limit = 10,
    question_limit = 5,
    summary_limit = 3
WHERE name = 'Free' OR name = 'FREE';

-- Also ensure other plans have proper limits if they don't already
UPDATE payment_plan 
SET 
    flashcard_limit = CASE WHEN flashcard_limit = 0 THEN 50 ELSE flashcard_limit END,
    question_limit = CASE WHEN question_limit = 0 THEN 20 ELSE question_limit END,
    summary_limit = CASE WHEN summary_limit = 0 THEN 10 ELSE summary_limit END
WHERE name = 'Pro';

UPDATE payment_plan 
SET 
    flashcard_limit = CASE WHEN flashcard_limit = 0 THEN -1 ELSE flashcard_limit END,
    question_limit = CASE WHEN question_limit = 0 THEN -1 ELSE question_limit END,
    summary_limit = CASE WHEN summary_limit = 0 THEN -1 ELSE summary_limit END
WHERE name = 'Premium';
