-- Test account
-- Username: tugn
-- Password: 123456

INSERT INTO account (
    id,
    username,
    email,
    password_hash,
    role,
    status
)
VALUES (
           '11111111-1111-1111-1111-111111111111',
           'tugn',
           'tugn@gmail.com',
           '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
           'USER',
           'ACTIVE'
       );