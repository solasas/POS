-- Milestone 2: turn the groundwork transaction tables into the sale model.
--   transactions      -> sales
--   transaction_items -> sale_items  (immutable product snapshots)
--   payments                          (cashier-recorded, never gateway-verified)
-- plus an append-only inventory ledger.

-- ---- sales ----------------------------------------------------------------------------
ALTER TABLE transactions RENAME TO sales;
ALTER INDEX idx_transactions_register_id RENAME TO idx_sales_register_id;
ALTER INDEX idx_transactions_cashier_id  RENAME TO idx_sales_cashier_id;
ALTER INDEX idx_transactions_customer_id RENAME TO idx_sales_customer_id;
ALTER INDEX idx_transactions_status      RENAME TO idx_sales_status;
ALTER TABLE sales RENAME CONSTRAINT fk_transactions_register TO fk_sales_register;
ALTER TABLE sales RENAME CONSTRAINT fk_transactions_customer TO fk_sales_customer;

CREATE SEQUENCE sale_number_seq START WITH 1 INCREMENT BY 1;

ALTER TABLE sales
    ALTER COLUMN register_id DROP NOT NULL,   -- web checkout has no register unless the token carries one
    ADD COLUMN sale_number        VARCHAR(40),
    ADD COLUMN cashier_name       VARCHAR(255),
    ADD COLUMN discount_percent   NUMERIC(5, 2) NOT NULL DEFAULT 0,
    ADD COLUMN prices_include_tax BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN payment_method     VARCHAR(16),
    ADD COLUMN payment_status     VARCHAR(16),
    ADD COLUMN idempotency_key    VARCHAR(64),
    ADD COLUMN request_hash       VARCHAR(64),
    ADD COLUMN sold_at            TIMESTAMPTZ  NOT NULL DEFAULT now();

-- rows created by the pre-checkout prototype (if any) get a recognisable legacy identity
UPDATE sales SET sale_number  = 'LEGACY-' || replace(id::text, '-', ''),
                 cashier_name = 'Legacy',
                 payment_status = 'RECORDED'
WHERE sale_number IS NULL;

ALTER TABLE sales
    ALTER COLUMN sale_number  SET NOT NULL,
    ALTER COLUMN cashier_name SET NOT NULL,
    ADD CONSTRAINT uq_sales_sale_number UNIQUE (sale_number),
    ADD CONSTRAINT chk_sales_discount_percent CHECK (discount_percent BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_sales_totals_non_negative
        CHECK (subtotal >= 0 AND discount_total >= 0 AND tax_total >= 0 AND grand_total >= 0),
    ADD CONSTRAINT chk_sales_payment_method CHECK (payment_method IS NULL OR payment_method IN ('CASH', 'UPI', 'CARD')),
    ADD CONSTRAINT chk_sales_payment_status CHECK (payment_status IS NULL OR payment_status IN ('RECORDED'));

-- the prototype OPEN/HELD states no longer exist; a sale is created already completed
ALTER TABLE sales DROP CONSTRAINT chk_transactions_status;
UPDATE sales SET status = 'VOIDED' WHERE status NOT IN ('COMPLETED', 'VOIDED');
ALTER TABLE sales ADD CONSTRAINT chk_sales_status CHECK (status IN ('COMPLETED', 'VOIDED'));
ALTER TABLE sales ALTER COLUMN status SET DEFAULT 'COMPLETED';

-- one sale per idempotency key; NULLs (legacy rows) are allowed to repeat
CREATE UNIQUE INDEX uq_sales_idempotency_key ON sales (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_sales_sold_at ON sales (sold_at DESC);
CREATE INDEX idx_sales_payment_method ON sales (payment_method);

-- ---- sale_items -----------------------------------------------------------------------
ALTER TABLE transaction_items RENAME TO sale_items;
ALTER TABLE sale_items RENAME COLUMN transaction_id TO sale_id;
ALTER INDEX idx_transaction_items_transaction_id RENAME TO idx_sale_items_sale_id;
ALTER INDEX idx_transaction_items_product_id     RENAME TO idx_sale_items_product_id;
ALTER TABLE sale_items RENAME CONSTRAINT fk_transaction_items_transaction TO fk_sale_items_sale;
ALTER TABLE sale_items RENAME CONSTRAINT fk_transaction_items_product TO fk_sale_items_product;
ALTER TABLE sale_items RENAME CONSTRAINT chk_transaction_items_quantity_positive TO chk_sale_items_quantity_positive;

ALTER TABLE sale_items
    ADD COLUMN product_name    VARCHAR(255),
    ADD COLUMN product_sku     VARCHAR(64),
    ADD COLUMN product_barcode VARCHAR(64),
    ADD COLUMN unit            VARCHAR(16),
    ADD COLUMN line_subtotal   NUMERIC(12, 2),
    ADD COLUMN tax_rate        NUMERIC(7, 4) NOT NULL DEFAULT 0,
    ADD COLUMN tax_amount      NUMERIC(12, 2) NOT NULL DEFAULT 0;

UPDATE sale_items si
SET product_name = p.name, product_sku = p.sku, product_barcode = p.barcode, unit = p.unit,
    line_subtotal = si.quantity * si.unit_price
FROM products p WHERE p.id = si.product_id AND si.product_name IS NULL;

ALTER TABLE sale_items
    ALTER COLUMN product_name  SET NOT NULL,
    ALTER COLUMN product_sku   SET NOT NULL,
    ALTER COLUMN unit          SET NOT NULL,
    ALTER COLUMN line_subtotal SET NOT NULL,
    ADD CONSTRAINT chk_sale_items_amounts_non_negative
        CHECK (unit_price >= 0 AND discount_amount >= 0 AND tax_amount >= 0 AND line_total >= 0 AND line_subtotal >= 0);

-- ---- payments -------------------------------------------------------------------------
ALTER TABLE payments RENAME COLUMN transaction_id TO sale_id;
ALTER INDEX idx_payments_transaction_id RENAME TO idx_payments_sale_id;
ALTER TABLE payments RENAME CONSTRAINT fk_payments_transaction TO fk_payments_sale;

-- Payments are recorded by the cashier; no gateway is involved, so there is no gateway reference.
ALTER TABLE payments DROP COLUMN gateway_ref;
ALTER TABLE payments DROP CONSTRAINT chk_payments_method;
DELETE FROM payments WHERE method NOT IN ('CASH', 'UPI', 'CARD');
ALTER TABLE payments
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'RECORDED',
    ADD CONSTRAINT chk_payments_method CHECK (method IN ('CASH', 'UPI', 'CARD')),
    ADD CONSTRAINT chk_payments_status CHECK (status IN ('RECORDED')),
    ADD CONSTRAINT chk_payments_amount_non_negative CHECK (amount >= 0),
    ADD CONSTRAINT chk_payments_cash_fields CHECK (
        method = 'CASH' OR (tendered IS NULL AND change_due IS NULL)),
    ADD CONSTRAINT chk_payments_tendered_covers_amount CHECK (
        tendered IS NULL OR tendered >= amount);
CREATE UNIQUE INDEX uq_payments_sale_id ON payments (sale_id);  -- one payment per sale in this milestone

-- ---- inventory ledger -----------------------------------------------------------------
-- Convention: quantity_change is SIGNED. Stock leaving (sale) is negative, stock arriving or a
-- positive correction is positive. stock_after = stock_before + quantity_change always holds.
CREATE TABLE inventory_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID           NOT NULL,
    movement_type   VARCHAR(16)    NOT NULL,
    quantity_change NUMERIC(12, 3) NOT NULL,
    stock_before    NUMERIC(12, 3) NOT NULL,
    stock_after     NUMERIC(12, 3) NOT NULL,
    sale_id         UUID,
    reason          VARCHAR(255),
    actor_id        UUID           NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT fk_inventory_tx_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_tx_sale    FOREIGN KEY (sale_id)    REFERENCES sales (id),
    CONSTRAINT chk_inventory_tx_type   CHECK (movement_type IN ('SALE', 'ADJUSTMENT')),
    CONSTRAINT chk_inventory_tx_nonzero CHECK (quantity_change <> 0),
    CONSTRAINT chk_inventory_tx_balance CHECK (stock_after = stock_before + quantity_change),
    CONSTRAINT chk_inventory_tx_stock_non_negative CHECK (stock_before >= 0 AND stock_after >= 0),
    CONSTRAINT chk_inventory_tx_sale_link CHECK ((movement_type = 'SALE') = (sale_id IS NOT NULL)),
    CONSTRAINT chk_inventory_tx_sale_negative CHECK (movement_type <> 'SALE' OR quantity_change < 0),
    CONSTRAINT chk_inventory_tx_adjustment_reason CHECK (movement_type <> 'ADJUSTMENT' OR reason IS NOT NULL)
);

CREATE INDEX idx_inventory_tx_product_created ON inventory_transactions (product_id, created_at DESC);
CREATE INDEX idx_inventory_tx_sale_id ON inventory_transactions (sale_id);

-- ---- immutability guards --------------------------------------------------------------
CREATE FUNCTION forbid_row_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_inventory_transactions_append_only
    BEFORE UPDATE OR DELETE ON inventory_transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_sale_items_immutable
    BEFORE UPDATE OR DELETE ON sale_items
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_payments_immutable
    BEFORE UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
CREATE TRIGGER trg_sales_no_delete
    BEFORE DELETE ON sales
    FOR EACH ROW EXECUTE FUNCTION forbid_row_change();
