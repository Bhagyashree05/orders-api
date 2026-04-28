-- V2__seed_products.sql
-- Deterministic UUIDs matching UUID.nameUUIDFromBytes("SKU-00N".getBytes()) in Java
INSERT INTO products (id, sku, name, price, active, created_at, updated_at) VALUES
    ('3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6','SKU-001','Wireless Headphones', 49.99,TRUE,now(),now()),
    ('b1c2d3e4-f5a6-3b7c-8d9e-0a1b2c3d4e5f','SKU-002','USB-C Hub',            29.99,TRUE,now(),now()),
    ('c2d3e4f5-a6b7-3c8d-9e0f-1a2b3c4d5e6f','SKU-003','Mechanical Keyboard',  89.99,TRUE,now(),now()),
    ('d3e4f5a6-b7c8-3d9e-0f1a-2b3c4d5e6f7a','SKU-004','Standing Desk Mat',    39.99,TRUE,now(),now()),
    ('e4f5a6b7-c8d9-3e0f-1a2b-3c4d5e6f7a8b','SKU-005','Webcam HD 1080p',      59.99,TRUE,now(),now())
ON CONFLICT (sku) DO NOTHING;
