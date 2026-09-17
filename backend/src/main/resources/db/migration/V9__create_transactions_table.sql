CREATE TABLE transactions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    register_id    UUID           NOT NULL,
    cashier_id     UUID           NOT NULL,
    customer_id    UUID,
    status         VARCHAR(16)    NOT NULL DEFAULT 'OPEN',
    subtotal       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    tax_total      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(12, 2) NOT NULL DEFAULT 0,
    grand_total    NUMERIC(12, 2) NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_transactions_register FOREIGN KEY (register_id) REFERENCES registers (id),
    CONSTRAINT fk_transactions_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_transactions_status CHECK (status IN ('OPEN', 'HELD', 'COMPLETED', 'VOIDED'))
);

CREATE INDEX idx_transactions_register_id ON transactions (register_id);
CREATE INDEX idx_transactions_cashier_id ON transactions (cashier_id);
CREATE INDEX idx_transactions_customer_id ON transactions (customer_id);
CREATE INDEX idx_transactions_status ON transactions (status);
