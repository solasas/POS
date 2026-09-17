CREATE TABLE transaction_items (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id   UUID           NOT NULL,
    product_id       UUID           NOT NULL,
    quantity         NUMERIC(12, 3) NOT NULL,
    unit_price       NUMERIC(12, 2) NOT NULL,
    discount_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    line_total       NUMERIC(12, 2) NOT NULL,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_transaction_items_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT fk_transaction_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_transaction_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_transaction_items_transaction_id ON transaction_items (transaction_id);
CREATE INDEX idx_transaction_items_product_id ON transaction_items (product_id);
