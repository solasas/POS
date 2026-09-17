CREATE TABLE products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku                VARCHAR(64)    NOT NULL,
    barcode            VARCHAR(64),
    name               VARCHAR(255)   NOT NULL,
    category_id        UUID           NOT NULL,
    unit_type          VARCHAR(16)    NOT NULL,
    price              NUMERIC(12, 2) NOT NULL,
    tax_class          VARCHAR(32)    NOT NULL,
    is_age_restricted  BOOLEAN        NOT NULL DEFAULT FALSE,
    reorder_point      INTEGER        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT uq_products_barcode UNIQUE (barcode),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_unit_type CHECK (unit_type IN ('EACH', 'WEIGHT')),
    CONSTRAINT chk_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_products_reorder_point_non_negative CHECK (reorder_point >= 0)
);

-- uq_products_sku / uq_products_barcode above already create unique indexes for lookup
CREATE INDEX idx_products_category_id ON products (category_id);
