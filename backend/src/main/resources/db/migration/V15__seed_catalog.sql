-- Development/demo catalog so a fresh environment has something to show.
-- Like V14, do not rely on this migration in production.

INSERT INTO categories (id, name) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Produce'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Dairy'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'Bakery'),
    ('aaaaaaaa-0000-0000-0000-000000000004', 'Beverages');

INSERT INTO products (id, sku, barcode, name, category_id, unit_type, price, tax_class, is_age_restricted, reorder_point) VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001', 'PRD-BANANA', '4011', 'Bananas (per kg)',
     'aaaaaaaa-0000-0000-0000-000000000001', 'WEIGHT', 1.29, 'ZERO', FALSE, 10),
    ('bbbbbbbb-0000-0000-0000-000000000002', 'DAI-MILK-1L', '5000000000011', 'Whole Milk 1L',
     'aaaaaaaa-0000-0000-0000-000000000002', 'EACH', 1.09, 'ZERO', FALSE, 20),
    ('bbbbbbbb-0000-0000-0000-000000000003', 'BAK-LOAF', '5000000000028', 'Sourdough Loaf',
     'aaaaaaaa-0000-0000-0000-000000000003', 'EACH', 3.49, 'STANDARD', FALSE, 5),
    ('bbbbbbbb-0000-0000-0000-000000000004', 'BEV-COLA-330', '5000000000035', 'Cola 330ml',
     'aaaaaaaa-0000-0000-0000-000000000004', 'EACH', 0.99, 'STANDARD', FALSE, 24),
    ('bbbbbbbb-0000-0000-0000-000000000005', 'BEV-LAGER-4PK', '5000000000042', 'Lager 4-pack',
     'aaaaaaaa-0000-0000-0000-000000000004', 'EACH', 6.99, 'STANDARD', TRUE, 6);
