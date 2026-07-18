-- =========================================================
-- V3.0  Add flashcard_cards per-generation limit
-- =========================================================
-- Purpose:
--   Adds a per-generation card count limit for flashcards,
--   analogous to ai_questions for quizzes.
--
--   New columns:
--     payment_plan.flashcard_cards
--     subscriptions.flashcard_cards_granted
-- =========================================================

ALTER TABLE payment_plan
    ADD COLUMN IF NOT EXISTS flashcard_cards INTEGER NOT NULL DEFAULT 10;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS flashcard_cards_granted INTEGER NOT NULL DEFAULT 10;

-- Backfill existing active subscriptions from their plan
UPDATE subscriptions s
SET flashcard_cards_granted = COALESCE(p.flashcard_cards, 10)
FROM payment_plan p
WHERE s.plan_id = p.id;
