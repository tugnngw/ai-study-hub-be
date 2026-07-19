-- =========================================================
-- V5__add_payment_tables.sql
-- =========================================================

-- 1. Tạo bảng payment_plan (mới)
CREATE TABLE IF NOT EXISTS payment_plan (
                                            id BIGSERIAL PRIMARY KEY,
                                            name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    storage_gb INTEGER,
    ai_questions INTEGER,
    price BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
    );

-- 2. Xóa bảng payment cũ (nếu tồn tại) và tạo lại với cấu trúc mới
DROP TABLE IF EXISTS payment CASCADE;

CREATE TABLE payment (
                         id BIGSERIAL PRIMARY KEY,
                         account_id UUID NOT NULL,
                         plan_id BIGINT,
                         payos_order_code BIGINT NOT NULL UNIQUE,
                         amount BIGINT NOT NULL,
                         status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                         description TEXT,
                         transaction_id VARCHAR(255),
                         payment_method VARCHAR(50),
                         expired_at TIMESTAMP,
                         created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                         FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE,
                         FOREIGN KEY (plan_id) REFERENCES payment_plan(id) ON DELETE SET NULL
);

-- 3. Tạo indexes
CREATE INDEX idx_payment_account_id ON payment(account_id);
CREATE INDEX idx_payment_order_code ON payment(payos_order_code);
CREATE INDEX idx_payment_status ON payment(status);

-- 4. Thêm quota columns vào account (nếu chưa có)
ALTER TABLE account ADD COLUMN IF NOT EXISTS storage_quota_gb INTEGER DEFAULT 0;
ALTER TABLE account ADD COLUMN IF NOT EXISTS ai_questions_quota INTEGER DEFAULT 0;

-- 5. Insert dữ liệu mẫu
INSERT INTO payment_plan (name, description, storage_gb, ai_questions, price, is_active) VALUES
                                                                                             ('Free', 'Basic plan ', 1, 5, 0, true),
                                                                                             ('Pro', 'Pro plan', 5, 20, 99000, true),
                                                                                             ('Premium', 'Premium plan', 10, 50, 150000, true)
    ON CONFLICT (name) DO NOTHING;