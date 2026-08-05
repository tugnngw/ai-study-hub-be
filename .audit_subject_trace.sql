-- Runtime trace: subject flow
-- 1. Subjects
SELECT id, semester_id, code, name FROM subject ORDER BY code LIMIT 5;
-- 2. Folders with subject
SELECT id, name, subject_id FROM folder WHERE deleted_at IS NULL LIMIT 5;
-- 3. Documents with subject
SELECT id, title, subject_id, folder_id FROM document WHERE deleted_at IS NULL LIMIT 5;
-- 4. Compare: same UUID casing?
SELECT
  count(*) AS total,
  count(DISTINCT d.subject_id) AS distinct_doc_subject,
  count(DISTINCT f.subject_id) AS distinct_folder_subject,
  count(DISTINCT s.id) AS distinct_subject
FROM document d
LEFT JOIN folder f ON f.id = d.folder_id
LEFT JOIN subject s ON s.id = d.subject_id
WHERE d.deleted_at IS NULL;
-- 5. Mismatch: doc.subject_id NOT in subject table
SELECT d.id AS doc_id, d.subject_id, d.folder_id
FROM document d LEFT JOIN subject s ON s.id = d.subject_id
WHERE d.deleted_at IS NULL AND s.id IS NULL
LIMIT 5;
-- 6. Mismatch: folder.subject_id NOT in subject table
SELECT f.id AS folder_id, f.subject_id
FROM folder f LEFT JOIN subject s ON s.id = f.subject_id
WHERE f.deleted_at IS NULL AND s.id IS NULL
LIMIT 5;
