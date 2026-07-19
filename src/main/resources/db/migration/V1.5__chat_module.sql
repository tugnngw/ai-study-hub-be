-- =========================================================
-- V1.5  Chat Module
-- =========================================================
-- Purpose:
--   Creates AI chat history infrastructure:
--   - chat_session (conversation groupings)
--   - chat_message (individual chat turns)
--   - ai_usage_log (token tracking for billing/quota)
--
-- Dependencies:
--   V1.0  (account references)
--   V1.2  (document reference on chat_session)
--
-- Notes:
--   chat_session.title is auto-set from first user message.
--   ai_usage_log.session_id is nullable because not all
--   AI features (summary, flashcards, quiz) create sessions.
--
-- =========================================================

CREATE TABLE chat_session (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  UUID NOT NULL,
    document_id UUID,
    title       VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_session_account  FOREIGN KEY (account_id)  REFERENCES account(id)  ON DELETE CASCADE,
    CONSTRAINT fk_session_document FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE SET NULL
);
CREATE INDEX idx_session_account  ON chat_session(account_id);
CREATE INDEX idx_session_document ON chat_session(document_id);

CREATE TABLE chat_message (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id        UUID NOT NULL,
    sender_type       VARCHAR(20) NOT NULL,
    content           TEXT NOT NULL,
    referenced_chunks JSONB,
    tokens_used       INTEGER,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_message_session FOREIGN KEY (session_id)
        REFERENCES chat_session(id) ON DELETE CASCADE
);
CREATE INDEX idx_message_session ON chat_message(session_id);

CREATE TABLE ai_usage_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id      UUID NOT NULL,
    session_id      UUID,
    total_tokens    INTEGER,
    estimated_cost  DECIMAL(10,4),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_usage_account FOREIGN KEY (account_id) REFERENCES account(id)     ON DELETE CASCADE,
    CONSTRAINT fk_usage_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE SET NULL
);
CREATE INDEX idx_usage_account ON ai_usage_log(account_id);
