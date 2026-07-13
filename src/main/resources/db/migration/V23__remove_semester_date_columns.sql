-- Remove start_date and end_date columns from semester table
ALTER TABLE semester DROP COLUMN IF EXISTS start_date;
ALTER TABLE semester DROP COLUMN IF EXISTS end_date;
