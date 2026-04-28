-- V1__schema.sql

CREATE TABLE products (
    id UUID NOT NULL PRIMARY KEY, sku VARCHAR(50) NOT NULL, name VARCHAR(255) NOT NULL,
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0), active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku));
CREATE INDEX idx_products_sku ON products(sku);

CREATE TABLE orders (
    id UUID NOT NULL PRIMARY KEY, idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(500), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING','PAID','FULFILLED','CANCELLED')));
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at DESC);
COMMENT ON COLUMN orders.version IS 'Hibernate @Version — optimistic locking CAS';

CREATE TABLE order_items (
    id UUID NOT NULL PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL, product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL CHECK (quantity >= 1), unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0));
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
COMMENT ON COLUMN order_items.product_name IS 'Price+name snapshot at order time — immune to catalog changes';

CREATE TABLE payments (
    id UUID NOT NULL PRIMARY KEY, order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    mode VARCHAR(20) NOT NULL, transaction_id VARCHAR(128) NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount > 0), currency VARCHAR(3) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_order_id UNIQUE (order_id),
    CONSTRAINT uq_payments_transaction UNIQUE (transaction_id),
    CONSTRAINT chk_payments_mode CHECK (mode IN ('CARD','UPI','CASH','BANK_TRANSFER')));
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_transaction_id ON payments(transaction_id);
COMMENT ON COLUMN payments.transaction_id IS 'External gateway ref — UNIQUE prevents duplicate payment recording';

-- Transactional Outbox: written atomically with order state change.
-- Polled by OutboxRelayScheduler → Kafka. Production: replace with Debezium CDC.
CREATE TABLE order_outbox (
    id UUID NOT NULL PRIMARY KEY, event_id VARCHAR(36) NOT NULL,
    order_id UUID NOT NULL, event_type VARCHAR(30) NOT NULL,
    previous_status VARCHAR(20), new_status VARCHAR(20) NOT NULL,
    cancel_reason VARCHAR(500), occurred_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0, last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_outbox_event_id UNIQUE (event_id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
    CONSTRAINT chk_outbox_event_type CHECK (event_type IN ('ORDER_CREATED','ORDER_PAID','ORDER_FULFILLED','ORDER_CANCELLED')));
CREATE INDEX idx_outbox_status ON order_outbox(status);
CREATE INDEX idx_outbox_created_at ON order_outbox(created_at ASC);
CREATE INDEX idx_outbox_order_id ON order_outbox(order_id);
COMMENT ON TABLE order_outbox IS 'Transactional outbox — guarantees at-least-once Kafka delivery';
COMMENT ON COLUMN order_outbox.event_id IS 'Consumer-side dedup key for at-least-once delivery';
