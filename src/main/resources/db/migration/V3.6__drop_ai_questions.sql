-- =========================================================
-- V3.6  Drop unused ai_questions columns
-- =========================================================
-- aiQuestions is not used for quota enforcement (QUESTION
-- limits come from question_limit). Removing both the plan
-- column and the subscription snapshot.
-- =========================================================

ALTER TABLE payment_plan
    DROP COLUMN IF EXISTS ai_questions;

ALTER TABLE subscriptions
    DROP COLUMN IF EXISTS ai_questions_granted;