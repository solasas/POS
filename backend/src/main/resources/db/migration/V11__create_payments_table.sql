CREATE TABLE payments (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID           NOT NULL,
    method         VARCHAR(16)    NOT NULL,
    amount         NUMERIC(12, 2) NOT NULL,
    tendered       NUMERIC(12, 2),
    change_due     NUMERIC(12, 2),
    gateway_ref    VARCHAR(128),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_payments_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH', 'CARD', 'UPI', 'WALLET', 'GIFT_CARD'))
);

CREATE INDEX idx_payments_transaction_id ON payments (transaction_id);
