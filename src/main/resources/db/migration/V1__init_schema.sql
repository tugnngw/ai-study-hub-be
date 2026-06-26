-- =========================================================
-- DATABASE SCHEMA
-- =========================================================

-- Enable pgvector extension (REQUIRED for vector operations)
CREATE EXTENSION IF NOT EXISTS "vector";

-- =========================================================
-- 1. ACCOUNT
-- =========================================================
CREATE TABLE account (
                         id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         username        VARCHAR(10) UNIQUE NOT NULL,
                         email           VARCHAR(40) UNIQUE,
                         password_hash   VARCHAR(255) NOT NULL,
                         full_name       VARCHAR(30),
                         avatar_url      TEXT,
                         role            VARCHAR(50) NOT NULL DEFAULT 'USER',
                         status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
                         auth_provider   VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
                         provider_id     VARCHAR(255),
                         last_login_at   TIMESTAMP WITH TIME ZONE,
                         created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         deleted_at      TIMESTAMP WITH TIME ZONE,
                         version         BIGINT NOT NULL DEFAULT 0
);

-- =========================================================
-- 2. SEMESTER
-- =========================================================
CREATE TABLE semester (
                          id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                          name        VARCHAR(100) NOT NULL,
                          start_date  DATE,
                          end_date    DATE
);

-- =========================================================
-- 3. SUBJECT
-- =========================================================
CREATE TABLE subject (
                         id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                         semester_id BIGINT REFERENCES semester(id) ON DELETE SET NULL,
                         code        VARCHAR(50),
                         name        VARCHAR(255)
);

-- =========================================================
-- 4. FOLDER
-- =========================================================
CREATE TABLE folder (
                        id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        owner_id    UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                        name        VARCHAR(255) NOT NULL,
                        ai_summary  TEXT,
                        created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                        updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                        deleted_at  TIMESTAMP
);

-- =========================================================
-- 5. DOCUMENT (UUID)
-- =========================================================
CREATE TABLE document (
                          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          owner_id        UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                          subject_id      BIGINT REFERENCES subject(id) ON DELETE SET NULL,
                          folder_id       UUID REFERENCES folder(id) ON DELETE SET NULL,
                          title           VARCHAR(255) NOT NULL,
                          description     TEXT,
                          summary         TEXT,
                          status          VARCHAR(50) NOT NULL DEFAULT 'processing',
                          cloudinary_url  VARCHAR(500),
                          public_id       VARCHAR(255),
                          mime_type       VARCHAR(100),
                          checksum        VARCHAR(255),
                          file_size       BIGINT,
                          total_pages     INTEGER,
                          created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                          deleted_at      TIMESTAMP
);

-- =========================================================
-- 6. DOCUMENT_CHUNK (UUID)
-- =========================================================
CREATE TABLE document_chunk (
                                id               BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                                document_id      UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                                chunk_index      INTEGER NOT NULL,
                                content          TEXT NOT NULL,
                                embedding_vector VECTOR(768)
);

-- =========================================================
-- 7. SHARE
-- =========================================================
CREATE TABLE share (
                       id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                       folder_id           UUID NOT NULL REFERENCES folder(id) ON DELETE CASCADE,
                       owner_id            UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                       shared_account_id   UUID REFERENCES account(id) ON DELETE CASCADE,
                       visibility          VARCHAR(50) NOT NULL DEFAULT 'private',
                       created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
                       UNIQUE (folder_id, shared_account_id)
);

-- =========================================================
-- 8. BOOKMARK (UUID)
-- =========================================================
CREATE TABLE bookmark (
                          id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                          account_id  UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                          document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                          created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
                          UNIQUE (account_id, document_id)
);

-- =========================================================
-- 9. CHAT_SESSION (UUID)
-- =========================================================
CREATE TABLE chat_session (
                              id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                              account_id  UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                              document_id UUID REFERENCES document(id) ON DELETE SET NULL,
                              title       VARCHAR(255),
                              created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 10. CHAT_MESSAGE
-- =========================================================
CREATE TABLE chat_message (
                              id                BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                              session_id        BIGINT NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
                              sender_type       VARCHAR(20) NOT NULL,
                              content           TEXT NOT NULL,
                              referenced_chunks JSONB,
                              tokens_used       INTEGER,
                              created_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 11. QUIZ (UUID)
-- =========================================================
CREATE TABLE quiz (
                      id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                      document_id     UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                      title           VARCHAR(255) NOT NULL,
                      generated_by_ai BOOLEAN NOT NULL DEFAULT TRUE,
                      created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 12. QUESTION
-- =========================================================
CREATE TABLE question (
                          id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                          quiz_id         BIGINT NOT NULL REFERENCES quiz(id) ON DELETE CASCADE,
                          content         TEXT NOT NULL,
                          option_a        TEXT,
                          option_b        TEXT,
                          option_c        TEXT,
                          option_d        TEXT,
                          correct_answer  VARCHAR(1) CHECK (correct_answer IN ('A','B','C','D')),
                          created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 13. FLASHCARD (UUID)
-- =========================================================
CREATE TABLE flashcard (
                           id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                           document_id     UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                           front_content   TEXT NOT NULL,
                           back_content    TEXT NOT NULL,
                           generated_by_ai BOOLEAN NOT NULL DEFAULT TRUE,
                           created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 14. REPORT (UUID)
-- =========================================================
CREATE TABLE report (
                        id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                        reporter_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                        document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                        reason      TEXT,
                        status      VARCHAR(50) NOT NULL DEFAULT 'pending',
                        created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 15. PAYMENT
-- =========================================================
CREATE TABLE payment (
                         id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                         account_id      UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                         plan_name       VARCHAR(100) NOT NULL,
                         amount          DECIMAL(12,2) NOT NULL,
                         currency        VARCHAR(10) NOT NULL DEFAULT 'VND',
                         payment_method  VARCHAR(50),
                         transaction_code VARCHAR(255) UNIQUE,
                         status          VARCHAR(50) NOT NULL DEFAULT 'pending',
                         created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                         expired_at      TIMESTAMP
);

-- =========================================================
-- 16. AI_USAGE_LOG
-- =========================================================
CREATE TABLE ai_usage_log (
                              id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                              account_id      UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                              session_id      BIGINT REFERENCES chat_session(id) ON DELETE SET NULL,
                              total_tokens    INTEGER,
                              estimated_cost  DECIMAL(10,4),
                              created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- 17. QUIZ_ATTEMPT
-- =========================================================
CREATE TABLE quiz_attempt (
                              id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                              quiz_id         BIGINT NOT NULL REFERENCES quiz(id) ON DELETE CASCADE,
                              account_id      UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                              score           INTEGER,
                              total_questions INTEGER,
                              status          VARCHAR(20) NOT NULL DEFAULT 'in_progress',
                              started_at      TIMESTAMP NOT NULL DEFAULT NOW(),
                              completed_at    TIMESTAMP
);

-- =========================================================
-- 18. QUIZ_ANSWER
-- =========================================================
CREATE TABLE quiz_answer (
                             id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                             attempt_id      BIGINT NOT NULL REFERENCES quiz_attempt(id) ON DELETE CASCADE,
                             question_id     BIGINT NOT NULL REFERENCES question(id) ON DELETE CASCADE,
                             selected_answer VARCHAR(1) CHECK (selected_answer IN ('A','B','C','D')),
                             is_correct      BOOLEAN,
                             answered_at     TIMESTAMP NOT NULL DEFAULT NOW(),
                             UNIQUE (attempt_id, question_id)
);

-- =========================================================
-- 19. FLASHCARD_PROGRESS
-- =========================================================
CREATE TABLE flashcard_progress (
                                    id                  BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                                    flashcard_id        BIGINT NOT NULL REFERENCES flashcard(id) ON DELETE CASCADE,
                                    account_id          UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                                    status              VARCHAR(20) NOT NULL DEFAULT 'new',
                                    review_count        INTEGER NOT NULL DEFAULT 0,
                                    last_reviewed_at    TIMESTAMP,
                                    next_review_at      TIMESTAMP,
                                    UNIQUE (flashcard_id, account_id)
);

-- =========================================================
-- 20. STUDY_REPORT (UUID)
-- =========================================================
CREATE TABLE study_report (
                              id              BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
                              document_id     UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                              account_id      UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                              title           VARCHAR(255) NOT NULL,
                              content         TEXT NOT NULL,
                              created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- =========================================================
-- INDEXES
-- =========================================================

-- Document & Folder Indexes
CREATE INDEX idx_document_owner       ON document(owner_id);
CREATE INDEX idx_document_subject     ON document(subject_id);
CREATE INDEX idx_document_folder      ON document(folder_id);
CREATE INDEX idx_document_deleted_at  ON document(deleted_at);

-- RAG Indexes
CREATE INDEX idx_document_chunk_doc   ON document_chunk(document_id);
CREATE INDEX idx_document_chunk_vector
    ON document_chunk
    USING hnsw (embedding_vector vector_cosine_ops);

-- Chat Indexes
CREATE INDEX idx_chat_message_session ON chat_message(session_id);
CREATE INDEX idx_chat_session_account ON chat_session(account_id);
CREATE INDEX idx_chat_session_document ON chat_session(document_id);

-- Study Material Indexes
CREATE INDEX idx_quiz_document        ON quiz(document_id);
CREATE INDEX idx_question_quiz        ON question(quiz_id);
CREATE INDEX idx_flashcard_document   ON flashcard(document_id);
CREATE INDEX idx_study_report_doc     ON study_report(document_id);

-- Progress & Tracking Indexes
CREATE INDEX idx_quiz_attempt_quiz    ON quiz_attempt(quiz_id);
CREATE INDEX idx_quiz_attempt_account ON quiz_attempt(account_id);
CREATE INDEX idx_quiz_answer_attempt  ON quiz_answer(attempt_id);
CREATE INDEX idx_flashcard_progress_account ON flashcard_progress(account_id);
CREATE INDEX idx_flashcard_progress_flashcard ON flashcard_progress(flashcard_id);

-- Social & Logging Indexes
CREATE INDEX idx_share_folder         ON share(folder_id);
CREATE INDEX idx_bookmark_account     ON bookmark(account_id);
CREATE INDEX idx_bookmark_document    ON bookmark(document_id);
CREATE INDEX idx_ai_usage_account     ON ai_usage_log(account_id);
CREATE INDEX idx_report_document      ON report(document_id);