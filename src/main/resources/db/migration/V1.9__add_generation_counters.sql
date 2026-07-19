-- =========================================================
-- V1.9  Add generation counters to document table
-- =========================================================
-- Purpose:
--   Adds flashcard_generations and quiz_generations columns
--   to track the number of AI generation clicks per document.
--   Each click = 1 consumption from the user's plan limit,
--   regardless of how many cards/questions are generated.
--
-- Migration for existing data:
--   Documents that already have flashcards → flashcard_generations = 1
--   Documents that already have quizzes   → quiz_generations = 1
--   (Each existing set represents 1 prior generation click)
-- =========================================================

ALTER TABLE document
    ADD COLUMN IF NOT EXISTS flashcard_generations INTEGER NOT NULL DEFAULT 0;

ALTER TABLE document
    ADD COLUMN IF NOT EXISTS quiz_generations INTEGER NOT NULL DEFAULT 0;

-- Set initial counter to 1 for documents that already have flashcards
UPDATE document d
SET flashcard_generations = 1
WHERE d.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM flashcard f WHERE f.document_id = d.id);

-- Set initial counter to 1 for documents that already have quizzes
UPDATE document d
SET quiz_generations = 1
WHERE d.deleted_at IS NULL
  AND EXISTS (SELECT 1 FROM quiz q WHERE q.document_id = d.id);
