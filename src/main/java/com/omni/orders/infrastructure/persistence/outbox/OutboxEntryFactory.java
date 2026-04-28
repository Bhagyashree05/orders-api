package com.omni.orders.infrastructure.persistence.outbox;

import com.omni.orders.domain.model.CustomerOrder;
import com.omni.orders.domain.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Factory for creating {@link OutboxEntry} instances.
 *
 * <p>Centralises outbox entry construction so the application service
 * never instantiates entries directly — keeping the service focused
 * on business rules rather than infrastructure concerns.
 */
@Component
public class OutboxEntryFactory {

    public OutboxEntry forCreated(CustomerOrder order) {
        return build(order.getId(), OrderEventType.ORDER_CREATED,
                null, order.getStatus(), null);
    }

    public OutboxEntry forPaid(CustomerOrder order, OrderStatus previousStatus) {
        return build(order.getId(), OrderEventType.ORDER_PAID,
                previousStatus, order.getStatus(), null);
    }

    public OutboxEntry forFulfilled(CustomerOrder order, OrderStatus previousStatus) {
        return build(order.getId(), OrderEventType.ORDER_FULFILLED,
                previousStatus, order.getStatus(), null);
    }

    public OutboxEntry forCancelled(CustomerOrder order, OrderStatus previousStatus, String reason) {
        return build(order.getId(), OrderEventType.ORDER_CANCELLED,
                previousStatus, order.getStatus(), reason);
    }

    private OutboxEntry build(UUID orderId, OrderEventType eventType,
                               OrderStatus previousStatus, OrderStatus newStatus,
                               String cancelReason) {
        return new OutboxEntry(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                orderId,
                eventType,
                previousStatus,
                newStatus,
                cancelReason,
                Instant.now()
        );
    }
}
