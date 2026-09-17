CREATE TABLE price_history (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID           NOT NULL,
    old_price  NUMERIC(12, 2) NOT NULL,
    new_price  NUMERIC(12, 2) NOT NULL,
    changed_at TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_price_history_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_price_history_product_id ON price_history (product_id);
