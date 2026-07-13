-- =========================================================
-- V22__insert_semester_and_subject_data.sql
-- Tạo cấu trúc học kỳ và môn học (không gắn thời gian)
-- =========================================================

-- =========================================================
-- 1. XÓA DỮ LIỆU CŨ (NẾU CÓ) - ĐỂ KHÔNG BỊ TRÙNG
-- =========================================================
DELETE FROM subject;
DELETE FROM semester;

-- =========================================================
-- 2. INSERT SEMESTERS (9 học kỳ)
-- =========================================================
INSERT INTO semester (id, name) VALUES
                                    (gen_random_uuid(), 'Học kỳ 1'),
                                    (gen_random_uuid(), 'Học kỳ 2'),
                                    (gen_random_uuid(), 'Học kỳ 3'),
                                    (gen_random_uuid(), 'Học kỳ 4'),
                                    (gen_random_uuid(), 'Học kỳ 5'),
                                    (gen_random_uuid(), 'Học kỳ 6'),
                                    (gen_random_uuid(), 'Học kỳ 7'),
                                    (gen_random_uuid(), 'Học kỳ 8'),
                                    (gen_random_uuid(), 'Học kỳ 9');

-- =========================================================
-- 3. INSERT SUBJECTS
-- =========================================================
WITH semester_ids AS (
    SELECT id, name FROM semester
)
INSERT INTO subject (id, semester_id, code, name, default_subject)
SELECT
    gen_random_uuid(),
    s.id,
    sub.code,
    sub.name,
    false
FROM semester_ids s
         JOIN (
    VALUES
        ('Học kỳ 1', 'CSI106', 'Introduction to Computer Science'),
        ('Học kỳ 1', 'SSL101c', 'Academic Skills for University Success'),
        ('Học kỳ 1', 'PRF192', 'Programming Fundamentals'),
        ('Học kỳ 1', 'MAE101', 'Mathematics for Engineering'),
        ('Học kỳ 1', 'CEA201', 'Computer Organization and Architecture'),
        ('Học kỳ 2', 'PRO192', 'Object-Oriented Programming'),
        ('Học kỳ 2', 'MAD101', 'Discrete Mathematics'),
        ('Học kỳ 2', 'OSG202', 'Operating Systems'),
        ('Học kỳ 2', 'NWC204', 'Computer Networking'),
        ('Học kỳ 2', 'SSG104', 'Communication and In-Group Working Skills'),
        ('Học kỳ 3', 'CSD201', 'Data Structures and Algorithms'),
        ('Học kỳ 3', 'DBI202', 'Database Systems'),
        ('Học kỳ 3', 'LAB211', 'OOP with Java Lab'),
        ('Học kỳ 3', 'JPD113', 'Elementary Japanese 1-A1.1'),
        ('Học kỳ 3', 'WED201c', 'Web Design'),
        ('Học kỳ 4', 'SWE201c', 'Introduction to Software Engineering'),
        ('Học kỳ 4', 'JPD123', 'Elementary Japanese 1-A1.2'),
        ('Học kỳ 4', 'IOT102', 'Internet of Things'),
        ('Học kỳ 4', 'PRJ301', 'Java Web Application Development'),
        ('Học kỳ 4', 'MAS291', 'Statistics & Probability'),
        ('Học kỳ 5', 'SWR302', 'Software Requirements'),
        ('Học kỳ 5', 'SWT301', 'Software Testing'),
        ('Học kỳ 5', 'SWP391', 'Software Development Project'),
        ('Học kỳ 5', 'WDU203c', 'The UI/UX Design'),
        ('Học kỳ 5', 'HSF302', 'Working with Spring Framework'),
        ('Học kỳ 6', 'ENW493c', 'Research Methods & Academic Writing Skills'),
        ('Học kỳ 6', 'OJT202', 'On the Job Training'),
        ('Học kỳ 7', 'EXE101', 'Experiential Entrepreneurship 1'),
        ('Học kỳ 7', 'PMG201c', 'Project Management'),
        ('Học kỳ 7', 'SBA301', 'Integrate SPA with Spring Boot'),
        ('Học kỳ 7', 'SWD392', 'Software Architecture and Design'),
        ('Học kỳ 8', 'MSS301', 'Microservices with Spring Cloud'),
        ('Học kỳ 8', 'PRM393', 'Mobile Programming'),
        ('Học kỳ 8', 'EXE201', 'Experiential Entrepreneurship 2'),
        ('Học kỳ 8', 'ITE302c', 'Ethics in IT'),
        ('Học kỳ 8', 'MLN122', 'Political Economics of Marxism – Leninism'),
        ('Học kỳ 8', 'MLN111', 'Philosophy of Marxism – Leninism'),
        ('Học kỳ 9', 'MLN131', 'Scientific Socialism'),
        ('Học kỳ 9', 'VNR202', 'History of Vietnam Communist Party'),
        ('Học kỳ 9', 'HCM202', 'Ho Chi Minh Ideology'),
        ('Học kỳ 9', 'SEP490', 'SE Capstone Project')
) AS sub(semester_name, code, name) ON s.name = sub.semester_name;

-- =========================================================
-- 4. ĐÁNH DẤU MÔN HỌC MẶC ĐỊNH (CHO USER MỚI)
-- =========================================================
UPDATE subject
SET default_subject = TRUE
WHERE code = 'PRF192';

-- =========================================================
-- 5. TẠO INDEXES
-- =========================================================
CREATE INDEX IF NOT EXISTS idx_subject_code ON subject(code);
CREATE INDEX IF NOT EXISTS idx_subject_name ON subject(name);
CREATE INDEX IF NOT EXISTS idx_subject_semester ON subject(semester_id);

-- =========================================================
-- 6. KIỂM TRA KẾT QUẢ
-- =========================================================
DO $$
DECLARE
semester_count INTEGER;
    subject_count INTEGER;
BEGIN
SELECT COUNT(*) INTO semester_count FROM semester;
SELECT COUNT(*) INTO subject_count FROM subject;

RAISE NOTICE '✅ Semesters: % (Expected: 9)', semester_count;
    RAISE NOTICE '✅ Subjects: % (Expected: 42)', subject_count;

    IF semester_count = 9 AND subject_count = 42 THEN
        RAISE NOTICE '✅ Migration V22 completed successfully!';
ELSE
        RAISE WARNING '⚠️ Data mismatch! Expected 9 semesters, 42 subjects';
END IF;
END $$;