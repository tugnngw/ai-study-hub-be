-- =========================================================
-- V1.0  Core
-- =========================================================
-- Purpose:
--   Creates foundational infrastructure:
--   - pgvector extension for vector similarity search
--   - account table (user identities and authentication)
--   - activity_log table (audit trail for user actions)
--
-- Dependencies: none
--
-- Notes:
--   All primary keys use UUID with gen_random_uuid().
--   Optimistic locking via version column (BIGINT).
--
-- =========================================================

CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE account (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50) NOT NULL,
    email           VARCHAR(40),
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(30),
    avatar_url      TEXT,
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    auth_provider   VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    provider_id     VARCHAR(255),
    plan            VARCHAR(50) NOT NULL DEFAULT 'FREE',
    storage_gb      DOUBLE PRECISION DEFAULT 1,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at      TIMESTAMP WITH TIME ZONE,
    version         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_account_username UNIQUE (username),
    CONSTRAINT uk_account_email UNIQUE (email)
);
CREATE INDEX idx_account_storage ON account(storage_gb);

CREATE TABLE activity_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    user_name   VARCHAR(255),
    action_type VARCHAR(50),
    description TEXT,
    metadata    TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
