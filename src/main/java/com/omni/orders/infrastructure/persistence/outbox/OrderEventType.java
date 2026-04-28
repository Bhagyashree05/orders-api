package com.omni.orders.infrastructure.persistence.outbox;

/**
 * Semantic event type for order state changes.
 * Stored in the outbox entry and included in the Kafka message envelope.
 */
public enum OrderEventType {
    ORDER_CREATED,
    ORDER_PAID,
    ORDER_FULFILLED,
    ORDER_CANCELLED
}
