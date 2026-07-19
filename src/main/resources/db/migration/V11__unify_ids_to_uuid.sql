-- =========================================================
-- V11: Unify all entity identifiers to UUID
-- =========================================================
-- This migration converts all remaining BIGINT/BIGSERIAL PKs
-- to UUID, along with all corresponding foreign key columns.
-- Existing data is preserved; new UUIDs are generated.

-- =========================================================
-- 0. Drop all FK constraints referencing tables being converted
-- =========================================================
ALTER TABLE subject DROP CONSTRAINT IF EXISTS subject_semester_id_fkey;
ALTER TABLE folder DROP CONSTRAINT IF EXISTS folder_subject_id_fkey;
ALTER TABLE document DROP CONSTRAINT IF EXISTS document_subject_id_fkey;
ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_plan_id_fkey;
ALTER TABLE question DROP CONSTRAINT IF EXISTS question_quiz_id_fkey;
ALTER TABLE quiz_attempt DROP CONSTRAINT IF EXISTS quiz_attempt_quiz_id_fkey;
ALTER TABLE quiz_answer DROP CONSTRAINT IF EXISTS quiz_answer_attempt_id_fkey;
ALTER TABLE quiz_answer DROP CONSTRAINT IF EXISTS quiz_answer_question_id_fkey;
ALTER TABLE flashcard_progress DROP CONSTRAINT IF EXISTS flashcard_progress_flashcard_id_fkey;

-- Also drop PK constraints (CASCADE handles dependent objects)
ALTER TABLE semester DROP CONSTRAINT IF EXISTS semester_pkey CASCADE;
ALTER TABLE subject DROP CONSTRAINT IF EXISTS subject_pkey CASCADE;
ALTER TABLE document_chunk DROP CONSTRAINT IF EXISTS document_chunk_pkey CASCADE;
ALTER TABLE share DROP CONSTRAINT IF EXISTS share_pkey CASCADE;
ALTER TABLE flashcard DROP CONSTRAINT IF EXISTS flashcard_pkey CASCADE;
ALTER TABLE quiz DROP CONSTRAINT IF EXISTS quiz_pkey CASCADE;
ALTER TABLE question DROP CONSTRAINT IF EXISTS question_pkey CASCADE;
ALTER TABLE quiz_attempt DROP CONSTRAINT IF EXISTS quiz_attempt_pkey CASCADE;
ALTER TABLE quiz_answer DROP CONSTRAINT IF EXISTS quiz_answer_pkey CASCADE;
ALTER TABLE flashcard_progress DROP CONSTRAINT IF EXISTS flashcard_progress_pkey CASCADE;
ALTER TABLE payment_plan DROP CONSTRAINT IF EXISTS payment_plan_pkey CASCADE;

-- =========================================================
-- 1. Build temporary mapping tables (old BIGINT → new UUID)
-- =========================================================
CREATE TEMP TABLE semester_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM semester;
CREATE TEMP TABLE subject_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM subject;
CREATE TEMP TABLE document_chunk_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM document_chunk;
CREATE TEMP TABLE share_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM share;
CREATE TEMP TABLE flashcard_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM flashcard;
CREATE TEMP TABLE quiz_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM quiz;
CREATE TEMP TABLE question_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM question;
CREATE TEMP TABLE quiz_attempt_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM quiz_attempt;
CREATE TEMP TABLE quiz_answer_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM quiz_answer;
CREATE TEMP TABLE flashcard_progress_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM flashcard_progress;
CREATE TEMP TABLE payment_plan_map AS SELECT id AS old_id, gen_random_uuid() AS new_id FROM payment_plan;

-- =========================================================
-- 2. Add new UUID PK columns and populate from mapping
-- =========================================================
ALTER TABLE semester ADD COLUMN id_new UUID;
UPDATE semester SET id_new = m.new_id FROM semester_map m WHERE semester.id = m.old_id;
ALTER TABLE semester ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE subject ADD COLUMN id_new UUID;
UPDATE subject SET id_new = m.new_id FROM subject_map m WHERE subject.id = m.old_id;
ALTER TABLE subject ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE document_chunk ADD COLUMN id_new UUID;
UPDATE document_chunk SET id_new = m.new_id FROM document_chunk_map m WHERE document_chunk.id = m.old_id;
ALTER TABLE document_chunk ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE share ADD COLUMN id_new UUID;
UPDATE share SET id_new = m.new_id FROM share_map m WHERE share.id = m.old_id;
ALTER TABLE share ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE flashcard ADD COLUMN id_new UUID;
UPDATE flashcard SET id_new = m.new_id FROM flashcard_map m WHERE flashcard.id = m.old_id;
ALTER TABLE flashcard ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE quiz ADD COLUMN id_new UUID;
UPDATE quiz SET id_new = m.new_id FROM quiz_map m WHERE quiz.id = m.old_id;
ALTER TABLE quiz ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE question ADD COLUMN id_new UUID;
UPDATE question SET id_new = m.new_id FROM question_map m WHERE question.id = m.old_id;
ALTER TABLE question ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE quiz_attempt ADD COLUMN id_new UUID;
UPDATE quiz_attempt SET id_new = m.new_id FROM quiz_attempt_map m WHERE quiz_attempt.id = m.old_id;
ALTER TABLE quiz_attempt ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE quiz_answer ADD COLUMN id_new UUID;
UPDATE quiz_answer SET id_new = m.new_id FROM quiz_answer_map m WHERE quiz_answer.id = m.old_id;
ALTER TABLE quiz_answer ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE flashcard_progress ADD COLUMN id_new UUID;
UPDATE flashcard_progress SET id_new = m.new_id FROM flashcard_progress_map m WHERE flashcard_progress.id = m.old_id;
ALTER TABLE flashcard_progress ALTER COLUMN id_new SET NOT NULL;

ALTER TABLE payment_plan ADD COLUMN id_new UUID;
UPDATE payment_plan SET id_new = m.new_id FROM payment_plan_map m WHERE payment_plan.id = m.old_id;
ALTER TABLE payment_plan ALTER COLUMN id_new SET NOT NULL;

-- =========================================================
-- 3. Add new UUID FK columns and populate from mapping
-- =========================================================

-- subject.semester_id → semester.id
ALTER TABLE subject ADD COLUMN semester_id_new UUID;
UPDATE subject SET semester_id_new = m.new_id FROM semester_map m WHERE subject.semester_id = m.old_id;

-- folder.subject_id → subject.id
ALTER TABLE folder ADD COLUMN subject_id_new UUID;
UPDATE folder SET subject_id_new = m.new_id FROM subject_map m WHERE folder.subject_id = m.old_id;

-- document.subject_id → subject.id
ALTER TABLE document ADD COLUMN subject_id_new UUID;
UPDATE document SET subject_id_new = m.new_id FROM subject_map m WHERE document.subject_id = m.old_id;

-- payment.plan_id → payment_plan.id
ALTER TABLE payment ADD COLUMN plan_id_new UUID;
UPDATE payment SET plan_id_new = m.new_id FROM payment_plan_map m WHERE payment.plan_id = m.old_id;

-- quiz_attempt.quiz_id → quiz.id
ALTER TABLE quiz_attempt ADD COLUMN quiz_id_new UUID;
UPDATE quiz_attempt SET quiz_id_new = m.new_id FROM quiz_map m WHERE quiz_attempt.quiz_id = m.old_id;

-- question.quiz_id → quiz.id
ALTER TABLE question ADD COLUMN quiz_id_new UUID;
UPDATE question SET quiz_id_new = m.new_id FROM quiz_map m WHERE question.quiz_id = m.old_id;

-- quiz_answer.attempt_id → quiz_attempt.id
ALTER TABLE quiz_answer ADD COLUMN attempt_id_new UUID;
UPDATE quiz_answer SET attempt_id_new = m.new_id FROM quiz_attempt_map m WHERE quiz_answer.attempt_id = m.old_id;

-- quiz_answer.question_id → question.id
ALTER TABLE quiz_answer ADD COLUMN question_id_new UUID;
UPDATE quiz_answer SET question_id_new = m.new_id FROM question_map m WHERE quiz_answer.question_id = m.old_id;

-- flashcard_progress.flashcard_id → flashcard.id
ALTER TABLE flashcard_progress ADD COLUMN flashcard_id_new UUID;
UPDATE flashcard_progress SET flashcard_id_new = m.new_id FROM flashcard_map m WHERE flashcard_progress.flashcard_id = m.old_id;

-- =========================================================
-- 4. Drop old PK columns
-- =========================================================
ALTER TABLE semester DROP COLUMN id;
ALTER TABLE semester RENAME COLUMN id_new TO id;
ALTER TABLE semester ADD PRIMARY KEY (id);

ALTER TABLE subject DROP COLUMN id;
ALTER TABLE subject RENAME COLUMN id_new TO id;
ALTER TABLE subject ADD PRIMARY KEY (id);

ALTER TABLE document_chunk DROP COLUMN id;
ALTER TABLE document_chunk RENAME COLUMN id_new TO id;
ALTER TABLE document_chunk ADD PRIMARY KEY (id);

ALTER TABLE share DROP COLUMN id;
ALTER TABLE share RENAME COLUMN id_new TO id;
ALTER TABLE share ADD PRIMARY KEY (id);

ALTER TABLE flashcard DROP COLUMN id;
ALTER TABLE flashcard RENAME COLUMN id_new TO id;
ALTER TABLE flashcard ADD PRIMARY KEY (id);

ALTER TABLE quiz DROP COLUMN id;
ALTER TABLE quiz RENAME COLUMN id_new TO id;
ALTER TABLE quiz ADD PRIMARY KEY (id);

ALTER TABLE question DROP COLUMN id;
ALTER TABLE question RENAME COLUMN id_new TO id;
ALTER TABLE question ADD PRIMARY KEY (id);

ALTER TABLE quiz_attempt DROP COLUMN id;
ALTER TABLE quiz_attempt RENAME COLUMN id_new TO id;
ALTER TABLE quiz_attempt ADD PRIMARY KEY (id);

ALTER TABLE quiz_answer DROP COLUMN id;
ALTER TABLE quiz_answer RENAME COLUMN id_new TO id;
ALTER TABLE quiz_answer ADD PRIMARY KEY (id);

ALTER TABLE flashcard_progress DROP COLUMN id;
ALTER TABLE flashcard_progress RENAME COLUMN id_new TO id;
ALTER TABLE flashcard_progress ADD PRIMARY KEY (id);

ALTER TABLE payment_plan DROP COLUMN id;
ALTER TABLE payment_plan RENAME COLUMN id_new TO id;
ALTER TABLE payment_plan ADD PRIMARY KEY (id);

-- Drop old FK columns, rename new ones
ALTER TABLE subject DROP COLUMN semester_id;
ALTER TABLE subject RENAME COLUMN semester_id_new TO semester_id;

ALTER TABLE folder DROP COLUMN subject_id;
ALTER TABLE folder RENAME COLUMN subject_id_new TO subject_id;

ALTER TABLE document DROP COLUMN subject_id;
ALTER TABLE document RENAME COLUMN subject_id_new TO subject_id;

ALTER TABLE payment DROP COLUMN plan_id;
ALTER TABLE payment RENAME COLUMN plan_id_new TO plan_id;

ALTER TABLE quiz_attempt DROP COLUMN quiz_id;
ALTER TABLE quiz_attempt RENAME COLUMN quiz_id_new TO quiz_id;

ALTER TABLE question DROP COLUMN quiz_id;
ALTER TABLE question RENAME COLUMN quiz_id_new TO quiz_id;

ALTER TABLE quiz_answer DROP COLUMN attempt_id;
ALTER TABLE quiz_answer RENAME COLUMN attempt_id_new TO attempt_id;

ALTER TABLE quiz_answer DROP COLUMN question_id;
ALTER TABLE quiz_answer RENAME COLUMN question_id_new TO question_id;

ALTER TABLE flashcard_progress DROP COLUMN flashcard_id;
ALTER TABLE flashcard_progress RENAME COLUMN flashcard_id_new TO flashcard_id;

-- =========================================================
-- 5. Re-add FK constraints
-- =========================================================
ALTER TABLE subject ADD CONSTRAINT subject_semester_id_fkey
    FOREIGN KEY (semester_id) REFERENCES semester(id) ON DELETE SET NULL;

ALTER TABLE folder ADD CONSTRAINT folder_subject_id_fkey
    FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE SET NULL;

ALTER TABLE document ADD CONSTRAINT document_subject_id_fkey
    FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE SET NULL;

ALTER TABLE question ADD CONSTRAINT question_quiz_id_fkey
    FOREIGN KEY (quiz_id) REFERENCES quiz(id) ON DELETE CASCADE;

ALTER TABLE quiz_attempt ADD CONSTRAINT quiz_attempt_quiz_id_fkey
    FOREIGN KEY (quiz_id) REFERENCES quiz(id) ON DELETE CASCADE;

ALTER TABLE quiz_answer ADD CONSTRAINT quiz_answer_attempt_id_fkey
    FOREIGN KEY (attempt_id) REFERENCES quiz_attempt(id) ON DELETE CASCADE;

ALTER TABLE quiz_answer ADD CONSTRAINT quiz_answer_question_id_fkey
    FOREIGN KEY (question_id) REFERENCES question(id) ON DELETE CASCADE;

ALTER TABLE flashcard_progress ADD CONSTRAINT flashcard_progress_flashcard_id_fkey
    FOREIGN KEY (flashcard_id) REFERENCES flashcard(id) ON DELETE CASCADE;

ALTER TABLE payment ADD CONSTRAINT payment_plan_id_fkey
    FOREIGN KEY (plan_id) REFERENCES payment_plan(id) ON DELETE SET NULL;

-- =========================================================
-- 6. Re-create UNIQUE constraints that were auto-dropped
--    with the old BIGINT FK columns in step 4
-- =========================================================
ALTER TABLE quiz_answer ADD CONSTRAINT uq_quiz_answer_attempt_question
    UNIQUE (attempt_id, question_id);

ALTER TABLE flashcard_progress ADD CONSTRAINT uq_flashcard_progress_flashcard_account
    UNIQUE (flashcard_id, account_id);

-- =========================================================
-- 7. Drop temp tables
-- =========================================================
DROP TABLE IF EXISTS semester_map;
DROP TABLE IF EXISTS subject_map;
DROP TABLE IF EXISTS document_chunk_map;
DROP TABLE IF EXISTS share_map;
DROP TABLE IF EXISTS flashcard_map;
DROP TABLE IF EXISTS quiz_map;
DROP TABLE IF EXISTS question_map;
DROP TABLE IF EXISTS quiz_attempt_map;
DROP TABLE IF EXISTS quiz_answer_map;
DROP TABLE IF EXISTS flashcard_progress_map;
DROP TABLE IF EXISTS payment_plan_map;
