-- Development/demo seed data: a register and one user per role, so a fresh
-- environment (including integration tests) is immediately usable without a
-- separate bootstrap step. Credentials are intentionally simple/non-secret
-- and documented in the README; do not rely on this migration in production.

INSERT INTO registers (id, code, name, is_active)
VALUES ('11111111-1111-1111-1111-111111111111', 'REG-1', 'Front Register 1', TRUE);

-- password: Admin#12345
INSERT INTO users (id, email, password_hash, pin_hash, name, role, is_active)
VALUES ('22222222-2222-2222-2222-222222222222', 'admin@pos.local',
        '$2a$10$LloWixzI/iwW53N3mfulRO2tyaqVNhI0JBE5vMV7IUxDfYQjfAc8.', NULL,
        'Ada Admin', 'ADMIN', TRUE);

-- password: Supervisor#12345, pin: 1111
INSERT INTO users (id, email, password_hash, pin_hash, name, role, is_active)
VALUES ('33333333-3333-3333-3333-333333333333', 'supervisor@pos.local',
        '$2a$10$EzBVap2Qz7CD/QiXMCXkRuPPvvc6QuUYd4GydKtTd3/wFmBNVgnpm',
        '$2a$10$HbzLsg8fF5UTs/Fs88E5We.x48812JYS25JTlV/1X9kYIi2yhw0W6',
        'Sam Supervisor', 'SUPERVISOR', TRUE);

-- pin: 2222 (no email/password: PIN-only cashier account)
INSERT INTO users (id, email, password_hash, pin_hash, name, role, is_active)
VALUES ('44444444-4444-4444-4444-444444444444', NULL, NULL,
        '$2a$10$A9r/4vKNXXYz4WqLkxbABO9QVzV0pUbMa9gxH4ClvwkKybLBUBMmK',
        'Cara Cashier', 'CASHIER', TRUE);
