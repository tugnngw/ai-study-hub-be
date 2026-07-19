CREATE TABLE report (
                        id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        reporter_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
                        document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
                        reason      TEXT,
                        status      VARCHAR(50) NOT NULL DEFAULT 'pending',
                        created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);