-- Milestone 1 catalog model: richer categories and products, case-insensitive
-- category names, free-form-ish units, and search indexes.

-- ---- categories -------------------------------------------------------------
ALTER TABLE categories
    ADD COLUMN description VARCHAR(1000),
    ADD COLUMN is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN updated_at  TIMESTAMPTZ NOT NULL DEFAULT now();

-- "Dairy" and "dairy" are the same category.
ALTER TABLE categories DROP CONSTRAINT uq_categories_name;
CREATE UNIQUE INDEX uq_categories_name_lower ON categories (lower(name));

CREATE INDEX idx_categories_is_active ON categories (is_active);

-- ---- products ---------------------------------------------------------------
ALTER TABLE products
    ADD COLUMN description    VARCHAR(1000),
    ADD COLUMN cost_price     NUMERIC(12, 2) NOT NULL DEFAULT 0,
    ADD COLUMN stock_quantity NUMERIC(12, 3) NOT NULL DEFAULT 0,
    ADD COLUMN unit           VARCHAR(16)    NOT NULL DEFAULT 'PIECE',
    ADD COLUMN is_active      BOOLEAN        NOT NULL DEFAULT TRUE;

-- unit_type (EACH/WEIGHT) is replaced by a richer unit of measurement.
UPDATE products SET unit = CASE unit_type WHEN 'WEIGHT' THEN 'KG' ELSE 'PIECE' END;
ALTER TABLE products DROP CONSTRAINT chk_products_unit_type;
ALTER TABLE products DROP COLUMN unit_type;
ALTER TABLE products
    ADD CONSTRAINT chk_products_unit CHECK (unit IN
        ('PIECE', 'KG', 'GRAM', 'LITRE', 'MILLILITRE', 'PACKET', 'BOX', 'DOZEN'));

-- reorder_point becomes the low-stock threshold (decimal, since weighed items are fractional).
ALTER TABLE products RENAME COLUMN reorder_point TO low_stock_threshold;
ALTER TABLE products ALTER COLUMN low_stock_threshold TYPE NUMERIC(12, 3);
ALTER TABLE products ALTER COLUMN low_stock_threshold SET DEFAULT 0;
ALTER TABLE products RENAME CONSTRAINT chk_products_reorder_point_non_negative
    TO chk_products_low_stock_threshold_non_negative;

ALTER TABLE products ALTER COLUMN tax_class SET DEFAULT 'STANDARD';

ALTER TABLE products
    ADD CONSTRAINT chk_products_cost_price_non_negative CHECK (cost_price >= 0),
    ADD CONSTRAINT chk_products_stock_quantity_non_negative CHECK (stock_quantity >= 0);

-- ---- search indexes ---------------------------------------------------------
-- Trigram index so "contains" name searches (ILIKE '%milk%') stay fast as the catalog grows.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING gin (lower(name) gin_trgm_ops);
CREATE INDEX idx_products_is_active ON products (is_active);
-- sku / barcode lookups use the unique indexes from V2; category filter uses idx_products_category_id.
