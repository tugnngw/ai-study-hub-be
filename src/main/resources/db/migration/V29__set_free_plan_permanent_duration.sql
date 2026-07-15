-- V29: Set FREE plan duration_days to -1 (permanent/unlimited)
-- -1 indicates that the plan never expires

UPDATE payment_plan 
SET duration_days = -1 
WHERE name = 'Free' OR name = 'FREE';
