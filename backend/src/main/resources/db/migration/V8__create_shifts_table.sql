CREATE TABLE shifts (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id   UUID           NOT NULL,
    cashier_id    UUID           NOT NULL,
    opening_cash  NUMERIC(12, 2) NOT NULL,
    closing_cash  NUMERIC(12, 2),
    opened_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    closed_at     TIMESTAMPTZ,

    CONSTRAINT fk_shifts_register FOREIGN KEY (register_id) REFERENCES registers (id),
    CONSTRAINT chk_shifts_closed_after_opened CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

CREATE INDEX idx_shifts_register_id ON shifts (register_id);
CREATE INDEX idx_shifts_cashier_id ON shifts (cashier_id);
