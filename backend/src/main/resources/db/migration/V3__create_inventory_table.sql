CREATE TABLE inventory (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id         UUID           NOT NULL,
    quantity_on_hand   NUMERIC(12, 3) NOT NULL DEFAULT 0,
    batch_no           VARCHAR(64),
    expiry_date        DATE,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_inventory_product_id ON inventory (product_id);
CREATE INDEX idx_inventory_expiry_date ON inventory (expiry_date);
