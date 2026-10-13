-- Snapshot the product's cost price on each sale line so profit can be calculated accurately even
-- after a product's cost is edited later (selling prices were already snapshotted).
ALTER TABLE sale_items ADD COLUMN unit_cost NUMERIC(12, 2) NOT NULL DEFAULT 0;

-- Existing lines predate the snapshot; the best available figure is the product's current cost.
-- The immutability trigger is lifted only for this one-off backfill.
ALTER TABLE sale_items DISABLE TRIGGER trg_sale_items_immutable;
UPDATE sale_items si SET unit_cost = p.cost_price FROM products p WHERE p.id = si.product_id;
ALTER TABLE sale_items ENABLE TRIGGER trg_sale_items_immutable;

ALTER TABLE sale_items ALTER COLUMN unit_cost DROP DEFAULT;
ALTER TABLE sale_items ADD CONSTRAINT chk_sale_items_unit_cost_non_negative CHECK (unit_cost >= 0);
