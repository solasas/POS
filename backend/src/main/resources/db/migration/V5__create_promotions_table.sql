CREATE TABLE promotions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type       VARCHAR(16) NOT NULL,
    rule_json  JSONB       NOT NULL,
    starts_at  TIMESTAMPTZ NOT NULL,
    ends_at    TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_promotions_type CHECK (type IN ('PERCENT', 'FLAT', 'BOGO', 'COMBO')),
    CONSTRAINT chk_promotions_date_range CHECK (ends_at > starts_at)
);

CREATE INDEX idx_promotions_starts_at_ends_at ON promotions (starts_at, ends_at);
