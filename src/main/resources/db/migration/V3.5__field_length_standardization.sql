-- V3.5: Standardize field lengths to match DTO and business rules
-- account.email: 40 → 255
ALTER TABLE account ALTER COLUMN email TYPE VARCHAR(255);

-- folder.name: 30 → 100
ALTER TABLE folder ALTER COLUMN name TYPE VARCHAR(100);

-- document/folder/report/chat: bound user-entered text
ALTER TABLE document ALTER COLUMN description TYPE VARCHAR(500);
ALTER TABLE folder ALTER COLUMN description TYPE VARCHAR(500);
ALTER TABLE report ALTER COLUMN reason TYPE VARCHAR(500);
ALTER TABLE report ALTER COLUMN admin_comment TYPE VARCHAR(500);
ALTER TABLE chat_message ALTER COLUMN content TYPE VARCHAR(2000);

-- payment_plan: add explicit lengths (previously no length = Hibernate default 255)
ALTER TABLE payment_plan ALTER COLUMN name TYPE VARCHAR(100);
ALTER TABLE payment_plan ALTER COLUMN description TYPE VARCHAR(500);
ALTER TABLE payment_plan ALTER COLUMN tagline TYPE VARCHAR(150);
