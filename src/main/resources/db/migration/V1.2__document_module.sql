-- =========================================================
-- V1.2  Document Module
-- =========================================================
-- Purpose:
--   Creates document storage infrastructure:
--   - folder (organizational units for documents)
--   - document (uploaded files with metadata)
--   - document_chunk (text chunks with vector embeddings for RAG)
--   - bookmark (user document bookmarks)
--
-- Dependencies:
--   V1.0  (account references)
--   V1.1  (subject references)
--
-- Notes:
--   document_chunk uses pgvector HNSW index for
--   efficient cosine similarity search.
--
-- =========================================================

CREATE TABLE folder (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    UUID NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    subject_id  UUID,
    ai_summary  TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP,
    CONSTRAINT fk_folder_owner   FOREIGN KEY (owner_id)   REFERENCES account(id) ON DELETE CASCADE,
    CONSTRAINT fk_folder_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE SET NULL
);
CREATE INDEX idx_folder_owner   ON folder(owner_id);
CREATE INDEX idx_folder_subject ON folder(subject_id);

CREATE TABLE document (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID NOT NULL,
    subject_id      UUID,
    folder_id       UUID,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    summary         TEXT,
    status          VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    ai_status       VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    reject_reason   TEXT,
    cloudinary_url  VARCHAR(500),
    public_id       VARCHAR(255),
    mime_type       VARCHAR(100),
    checksum        VARCHAR(255),
    file_size       BIGINT,
    total_pages     INTEGER,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP,
    CONSTRAINT fk_document_owner   FOREIGN KEY (owner_id)   REFERENCES account(id) ON DELETE CASCADE,
    CONSTRAINT fk_document_subject FOREIGN KEY (subject_id) REFERENCES subject(id) ON DELETE SET NULL,
    CONSTRAINT fk_document_folder  FOREIGN KEY (folder_id)  REFERENCES folder(id) ON DELETE SET NULL
);
CREATE INDEX idx_document_owner   ON document(owner_id);
CREATE INDEX idx_document_subject ON document(subject_id);
CREATE INDEX idx_document_folder  ON document(folder_id);
CREATE INDEX idx_document_deleted ON document(deleted_at);

CREATE TABLE document_chunk (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID NOT NULL,
    chunk_index      INTEGER NOT NULL,
    content          TEXT NOT NULL,
    embedding_vector VECTOR(768),
    CONSTRAINT fk_chunk_document FOREIGN KEY (document_id)
        REFERENCES document(id) ON DELETE CASCADE
);
CREATE INDEX idx_chunk_document ON document_chunk(document_id);
CREATE INDEX idx_chunk_vector   ON document_chunk
    USING hnsw (embedding_vector vector_cosine_ops);

CREATE TABLE bookmark (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  UUID NOT NULL,
    document_id UUID NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_bookmark_account  FOREIGN KEY (account_id)  REFERENCES account(id)  ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_document FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE,
    CONSTRAINT uk_bookmark UNIQUE (account_id, document_id)
);
CREATE INDEX idx_bookmark_account  ON bookmark(account_id);
CREATE INDEX idx_bookmark_document ON bookmark(document_id);
