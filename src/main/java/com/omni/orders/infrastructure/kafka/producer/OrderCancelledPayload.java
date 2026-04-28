package com.omni.orders.infrastructure.kafka.producer;

import com.omni.orders.domain.model.OrderStatus;

/**
 * Payload for {@link com.omni.orders.infrastructure.persistence.outbox.OrderEventType#ORDER_CANCELLED}.
 *
 * <p>Includes {@code reason} so ERP and warehouse consumers can void/release
 * stock with the correct cancellation reason without an extra API call.
 */
public class OrderCancelledPayload {

    private final OrderStatus previousStatus;
    private final OrderStatus newStatus;
    private final String      reason;

    public OrderCancelledPayload(OrderStatus previousStatus,
                                  OrderStatus newStatus,
                                  String reason) {
        this.previousStatus = previousStatus;
        this.newStatus      = newStatus;
        this.reason         = reason;
    }

    public OrderStatus getPreviousStatus() { return previousStatus; }
    public OrderStatus getNewStatus()      { return newStatus; }
    public String      getReason()         { return reason; }
}
