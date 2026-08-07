-- =========================================================
-- V2.0  Add subscription snapshot columns for plan limits
-- =========================================================
-- Purpose:
--   Snapshots additional plan limits at subscription creation
--   time so that existing subscribers keep their entitled
--   limits even after the plan is later modified.
--
--   Fields already snapshotted: price_paid, storage_gb_granted.
--
--   New snapshot columns:
--     flashcard_limit_granted
--     question_limit_granted
--     summary_limit_granted
--     chat_limit_granted
--     tier_granted
-- =========================================================

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS flashcard_limit_granted INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS question_limit_granted  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS summary_limit_granted   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS chat_limit_granted      INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS tier_granted            INTEGER NOT NULL DEFAULT 0;

-- Backfill existing active subscriptions from their plan
UPDATE subscriptions s
SET
    flashcard_limit_granted = COALESCE(p.flashcard_limit, 0),
    question_limit_granted  = COALESCE(p.question_limit, 0),
    summary_limit_granted   = COALESCE(p.summary_limit, 0),
    chat_limit_granted      = COALESCE(p.chat_limit, 0),
    tier_granted            = COALESCE(p.tier, 0)
FROM payment_plan p
WHERE s.plan_id = p.id;
