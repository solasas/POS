CREATE TABLE customers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone          VARCHAR(20)  NOT NULL,
    name           VARCHAR(255) NOT NULL,
    loyalty_points INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_customers_phone UNIQUE (phone),
    CONSTRAINT chk_customers_loyalty_points_non_negative CHECK (loyalty_points >= 0)
);
