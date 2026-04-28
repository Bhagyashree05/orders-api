package com.omni.orders.infrastructure.kafka.producer;

import com.omni.orders.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Payload for ORDER_CREATED events.
 *
 * <p>Items list may be null when built from an outbox entry, because the outbox
 * table intentionally does not store item details (keeping it small).
 * Consumers that need full item data call GET /api/v1/orders/{orderId}.
 */
public class OrderCreatedPayload {

    private final OrderStatus          status;
    private final BigDecimal           totalAmount;
    private final String               currency;
    private final List<OrderItemPayload> items;

    public OrderCreatedPayload(OrderStatus status,
                                BigDecimal totalAmount,
                                String currency,
                                List<OrderItemPayload> items) {
        this.status      = status;
        this.totalAmount = totalAmount;
        this.currency    = currency;
        // items may be null when built from outbox entry — guard defensively
        this.items       = (items != null) ? List.copyOf(items) : Collections.emptyList();
    }

    public OrderStatus            getStatus()      { return status; }
    public BigDecimal             getTotalAmount() { return totalAmount; }
    public String                 getCurrency()    { return currency; }
    public List<OrderItemPayload> getItems()       { return items; }
}
