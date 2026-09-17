CREATE TABLE categories (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_categories_name UNIQUE (name)
);
