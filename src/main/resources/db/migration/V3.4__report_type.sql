-- =========================================================
-- V3.4  report.type — phân loại REPORT vs APPEAL
-- =========================================================
-- Purpose:
--   Appeal tái sử dụng bảng report (không tạo bảng mới).
--   type = 'REPORT' (mặc định, dữ liệu cũ) | 'APPEAL' (kháng cáo doc bị BANNED).
-- =========================================================

ALTER TABLE report
    ADD COLUMN type VARCHAR(20) NOT NULL DEFAULT 'REPORT';

-- Backfill dữ liệu cũ (toàn bộ là REPORT)
UPDATE report SET type = 'REPORT' WHERE type IS NULL OR type = '';
