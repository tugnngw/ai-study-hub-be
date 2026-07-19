-- V8__change_payment_id_to_uuid.sql
-- Change payment table id from BIGSERIAL to UUID

ALTER TABLE payment DROP CONSTRAINT IF EXISTS payment_pkey CASCADE;

ALTER TABLE payment RENAME COLUMN id TO id_old;

ALTER TABLE payment ADD COLUMN id UUID DEFAULT gen_random_uuid();

UPDATE payment SET id = gen_random_uuid() WHERE id IS NULL;

ALTER TABLE payment ALTER COLUMN id SET NOT NULL;

ALTER TABLE payment ADD PRIMARY KEY (id);

ALTER TABLE payment DROP COLUMN id_old;
