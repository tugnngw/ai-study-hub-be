-- Add reject_reason column to document table for admin rejection reason
ALTER TABLE document ADD COLUMN IF NOT EXISTS reject_reason TEXT;
