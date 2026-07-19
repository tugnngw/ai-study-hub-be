-- =========================================================
-- V1.3  Sharing and Reports
-- =========================================================
-- Purpose:
--   Creates sharing and moderation features:
--   - share (folder/document sharing between users)
--   - report (user-generated document reports)
--
-- Dependencies:
--   V1.0  (account references)
--   V1.2  (folder and document references)
--
-- Notes:
--   A share links to either a folder OR a document.
--   share_token enables token-based access without login.
--
-- =========================================================

CREATE TABLE share (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    folder_id           UUID,
    document_id         UUID,
    owner_id            UUID NOT NULL,
    shared_account_id   UUID,
    share_token         VARCHAR(36),
    visibility          VARCHAR(50) NOT NULL DEFAULT 'private',
    expires_at          TIMESTAMP,
    revoked             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_share_folder    FOREIGN KEY (folder_id)         REFERENCES folder(id)   ON DELETE CASCADE,
    CONSTRAINT fk_share_document  FOREIGN KEY (document_id)       REFERENCES document(id) ON DELETE CASCADE,
    CONSTRAINT fk_share_owner     FOREIGN KEY (owner_id)          REFERENCES account(id)  ON DELETE CASCADE,
    CONSTRAINT fk_share_account   FOREIGN KEY (shared_account_id) REFERENCES account(id)  ON DELETE CASCADE,
    CONSTRAINT uk_share_token UNIQUE (share_token)
);
CREATE INDEX idx_share_folder  ON share(folder_id);
CREATE INDEX idx_share_document ON share(document_id);
CREATE INDEX idx_share_owner   ON share(owner_id);
CREATE INDEX idx_share_account ON share(shared_account_id);

CREATE TABLE report (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id   UUID NOT NULL,
    document_id   UUID NOT NULL,
    reason        TEXT,
    status        VARCHAR(50) NOT NULL DEFAULT 'pending',
    admin_comment TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES account(id)  ON DELETE CASCADE,
    CONSTRAINT fk_report_document FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE CASCADE
);
CREATE INDEX idx_report_document ON report(document_id);
