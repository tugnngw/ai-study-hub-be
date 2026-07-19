-- Add subject_id to folder table for Semester → Subject → Folder hierarchy
ALTER TABLE folder ADD COLUMN subject_id BIGINT REFERENCES subject(id) ON DELETE SET NULL;

CREATE INDEX idx_folder_subject ON folder(subject_id);
