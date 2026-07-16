-- =========================================================
-- V1.7  Development Seed Data
-- =========================================================
-- Purpose:
--   Inserts initial data required for development and
--   demonstration environments.
--
-- Dependencies:
--   All prior migrations (V1.0 through V1.6)
--
-- WARNING:
--   This migration is intended for DEVELOPMENT only.
--   Do NOT execute against production databases.
--
-- Contents:
--   1. Development account
--      username: tugn
--      password stored as BCrypt hash
--      default plaintext development password: 123456
--   2. Payment plans (Free, Pro, Premium)
--   3. Academic semesters and subjects (SE curriculum)
-- =========================================================

-- =========================================================
-- 1. Development Account
-- =========================================================
INSERT INTO account (
    id, username, email, password_hash, role, status
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'tugn',
    'tugn@gmail.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'USER',
    'ACTIVE'
);

-- =========================================================
-- 2. Payment Plans
--    Free:     default tier, permanent, feature-limited
--    Pro:      paid monthly, standard limits
--    Premium:  paid monthly, unlimited features
-- =========================================================
INSERT INTO payment_plan (id, name, description, storage_gb, price, is_active, duration_days, is_popular, display_order, tagline, flashcard_limit, question_limit, summary_limit) VALUES
    (gen_random_uuid(), 'Free',     'Basic plan',   1,      0,      true, -1,  false, 0, NULL,             10,  5,  3,  20),
    (gen_random_uuid(), 'Pro',      'Pro plan',     5,      99000,  true, 30, false, 0, NULL,             50,  20, 10, 100),
    (gen_random_uuid(), 'Premium',  'Premium plan', 10,     150000, true, 30, true,  0, 'Most popular',   -1, -1, -1, -1)
ON CONFLICT (name) DO NOTHING;

-- =========================================================
-- 3. Academic Semesters and Subjects
--    9 semesters, 50 subjects (9 General + 41 SE curriculum)
--    Each semester has exactly one default (General) subject.
-- =========================================================
INSERT INTO semester (id, name) VALUES
    (gen_random_uuid(), 'Semester 1'),
    (gen_random_uuid(), 'Semester 2'),
    (gen_random_uuid(), 'Semester 3'),
    (gen_random_uuid(), 'Semester 4'),
    (gen_random_uuid(), 'Semester 5'),
    (gen_random_uuid(), 'Semester 6'),
    (gen_random_uuid(), 'Semester 7'),
    (gen_random_uuid(), 'Semester 8'),
    (gen_random_uuid(), 'Semester 9');

WITH semester_ids AS (SELECT id, name FROM semester)
INSERT INTO subject (id, semester_id, code, name, default_subject)
SELECT gen_random_uuid(), s.id, sub.code, sub.name, sub.is_default
FROM semester_ids s
JOIN (VALUES
    ('Semester 1', 'GEN101', 'General',                                   true),
    ('Semester 1', 'CSI106', 'Introduction to Computer Science',          false),
    ('Semester 1', 'SSL101c','Academic Skills for University Success',     false),
    ('Semester 1', 'PRF192', 'Programming Fundamentals',                   false),
    ('Semester 1', 'MAE101', 'Mathematics for Engineering',                false),
    ('Semester 1', 'CEA201', 'Computer Organization and Architecture',     false),
    ('Semester 2', 'GEN102', 'General',                                   true),
    ('Semester 2', 'PRO192', 'Object-Oriented Programming',                false),
    ('Semester 2', 'MAD101', 'Discrete Mathematics',                       false),
    ('Semester 2', 'OSG202', 'Operating Systems',                          false),
    ('Semester 2', 'NWC204', 'Computer Networking',                        false),
    ('Semester 2', 'SSG104', 'Communication and In-Group Working Skills',  false),
    ('Semester 3', 'GEN103', 'General',                                   true),
    ('Semester 3', 'CSD201', 'Data Structures and Algorithms',             false),
    ('Semester 3', 'DBI202', 'Database Systems',                           false),
    ('Semester 3', 'LAB211', 'OOP with Java Lab',                         false),
    ('Semester 3', 'JPD113', 'Elementary Japanese 1-A1.1',                false),
    ('Semester 3', 'WED201c','Web Design',                                 false),
    ('Semester 4', 'GEN104', 'General',                                   true),
    ('Semester 4', 'SWE201c','Introduction to Software Engineering',       false),
    ('Semester 4', 'JPD123', 'Elementary Japanese 1-A1.2',                false),
    ('Semester 4', 'IOT102', 'Internet of Things',                         false),
    ('Semester 4', 'PRJ301', 'Java Web Application Development',          false),
    ('Semester 4', 'MAS291', 'Statistics & Probability',                   false),
    ('Semester 5', 'GEN105', 'General',                                   true),
    ('Semester 5', 'SWR302', 'Software Requirements',                      false),
    ('Semester 5', 'SWT301', 'Software Testing',                           false),
    ('Semester 5', 'SWP391', 'Software Development Project',               false),
    ('Semester 5', 'WDU203c','The UI/UX Design',                          false),
    ('Semester 5', 'HSF302', 'Working with Spring Framework',              false),
    ('Semester 6', 'GEN106', 'General',                                   true),
    ('Semester 6', 'ENW493c','Research Methods & Academic Writing Skills', false),
    ('Semester 6', 'OJT202', 'On the Job Training',                        false),
    ('Semester 7', 'GEN107', 'General',                                   true),
    ('Semester 7', 'EXE101', 'Experiential Entrepreneurship 1',            false),
    ('Semester 7', 'PMG201c','Project Management',                         false),
    ('Semester 7', 'SBA301', 'Integrate SPA with Spring Boot',             false),
    ('Semester 7', 'SWD392', 'Software Architecture and Design',           false),
    ('Semester 8', 'GEN108', 'General',                                   true),
    ('Semester 8', 'MSS301', 'Microservices with Spring Cloud',            false),
    ('Semester 8', 'PRM393', 'Mobile Programming',                         false),
    ('Semester 8', 'EXE201', 'Experiential Entrepreneurship 2',            false),
    ('Semester 8', 'ITE302c','Ethics in IT',                               false),
    ('Semester 8', 'MLN122', 'Political Economics of Marxism-Leninism',    false),
    ('Semester 8', 'MLN111', 'Philosophy of Marxism-Leninism',             false),
    ('Semester 9', 'GEN109', 'General',                                   true),
    ('Semester 9', 'MLN131', 'Scientific Socialism',                       false),
    ('Semester 9', 'VNR202', 'History of Vietnam Communist Party',         false),
    ('Semester 9', 'HCM202', 'Ho Chi Minh Ideology',                       false),
    ('Semester 9', 'SEP490', 'SE Capstone Project',                        false)
) AS sub(semester_name, code, name, is_default) ON s.name = sub.semester_name;
