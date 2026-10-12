-- SUPERVISOR is now MANAGER.
ALTER TABLE users DROP CONSTRAINT chk_users_role;
UPDATE users SET role = 'MANAGER' WHERE role = 'SUPERVISOR';
ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('CASHIER', 'MANAGER', 'ADMIN'));

-- Remove the well-known dev accounts (and their published passwords/PINs) seeded by
-- V14__seed_dev_data.sql. The first administrator is now created at startup from the
-- ADMIN_EMAIL / ADMIN_PASSWORD environment variables (see AdminBootstrap).
-- The REG-1 register from V14 is intentionally kept: it holds no credentials.
DELETE FROM users
WHERE id IN ('22222222-2222-2222-2222-222222222222',
             '33333333-3333-3333-3333-333333333333',
             '44444444-4444-4444-4444-444444444444');
