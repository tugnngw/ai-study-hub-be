-- =========================================================
-- V1.1  Academic Structure
-- =========================================================
-- Purpose:
--   Creates semester and subject tables for the academic
--   curriculum hierarchy.
--
-- Dependencies:
--   V1.0  (subject.semester_id references semester.id)
--
-- Notes:
--   default_subject flag identifies the default subject
--   for each semester (used during user onboarding).
--
-- =========================================================

CREATE TABLE semester (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL
);

CREATE TABLE subject (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    semester_id     UUID,
    code            VARCHAR(50),
    name            VARCHAR(255),
    default_subject BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_subject_semester FOREIGN KEY (semester_id)
        REFERENCES semester(id) ON DELETE SET NULL
);
CREATE INDEX idx_subject_code     ON subject(code);
CREATE INDEX idx_subject_name     ON subject(name);
CREATE INDEX idx_subject_semester ON subject(semester_id);
