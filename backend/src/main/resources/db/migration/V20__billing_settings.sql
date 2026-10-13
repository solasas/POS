-- Store-level settings (key/value). First setting: whether billing may continue after stock
-- reaches zero. Default is OFF, i.e. the previous strict behaviour.
CREATE TABLE app_settings (
    key        VARCHAR(64)  PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by UUID
);

INSERT INTO app_settings (key, value) VALUES ('allow_negative_stock', 'false');

-- With "allow billing out of stock" switched on, selling past zero leaves a negative balance that
-- a later stock receipt brings back up. When the switch is off the application still refuses to
-- sell or adjust below zero (under the same product row lock), so these checks are no longer
-- needed in the database. The ledger equation stock_after = stock_before + quantity_change stays.
ALTER TABLE products DROP CONSTRAINT chk_products_stock_quantity_non_negative;
ALTER TABLE inventory_transactions DROP CONSTRAINT chk_inventory_tx_stock_non_negative;
