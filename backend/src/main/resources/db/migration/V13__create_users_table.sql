CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255),
    password_hash VARCHAR(255),
    pin_hash      VARCHAR(255),
    name          VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CASHIER', 'SUPERVISOR', 'ADMIN')),
    CONSTRAINT chk_users_has_credential CHECK (password_hash IS NOT NULL OR pin_hash IS NOT NULL)
);

-- speeds up the PIN-login candidate scan (active users that have a PIN set)
CREATE INDEX idx_users_pin_candidates ON users (is_active) WHERE pin_hash IS NOT NULL;
