-- SKUs are stored upper-case by the application; normalise any older rows and make the
-- database enforce case-insensitive uniqueness so it cannot be bypassed.
UPDATE products SET sku = upper(sku) WHERE sku <> upper(sku);

CREATE UNIQUE INDEX uq_products_sku_upper ON products (upper(sku));
