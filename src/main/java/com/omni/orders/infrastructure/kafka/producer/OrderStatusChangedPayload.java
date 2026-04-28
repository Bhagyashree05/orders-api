package com.omni.orders.infrastructure.kafka.producer;

import com.omni.orders.domain.model.OrderStatus;

/**
 * Payload for {@link com.omni.orders.infrastructure.persistence.outbox.OrderEventType#ORDER_PAID}
 * and {@link com.omni.orders.infrastructure.persistence.outbox.OrderEventType#ORDER_FULFILLED}.
 *
 * <p>Minimal — consumers that need full order data (items, amounts) fetch from
 * {@code GET /api/v1/orders/{orderId}} using the {@code orderId} from the envelope.
 */
public class OrderStatusChangedPayload {

    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;

    public OrderStatusChangedPayload(OrderStatus previousStatus, OrderStatus newStatus) {
        this.previousStatus = previousStatus;
        this.newStatus      = newStatus;
    }

    public OrderStatus getPreviousStatus() { return previousStatus; }
    public OrderStatus getNewStatus()      { return newStatus; }
}
