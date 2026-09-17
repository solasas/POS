CREATE TABLE registers (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code       VARCHAR(32)  NOT NULL,
    name       VARCHAR(120),
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_registers_code UNIQUE (code)
);
