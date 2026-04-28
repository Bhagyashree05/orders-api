package com.omni.orders.infrastructure.kafka.producer;

import com.omni.orders.domain.model.OrderStatus;
import com.omni.orders.infrastructure.persistence.outbox.OrderEventType;
import com.omni.orders.infrastructure.persistence.outbox.OutboxEntry;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Builds typed {@link OrderEventMessage} instances from {@link OutboxEntry} records.
 *
 * <p>The generic payload type is resolved from {@link OrderEventType}:
 * <ul>
 *   <li>{@code ORDER_CREATED}  → {@link OrderCreatedPayload} (includes items from outbox)</li>
 *   <li>{@code ORDER_PAID}     → {@link OrderStatusChangedPayload}</li>
 *   <li>{@code ORDER_FULFILLED}→ {@link OrderStatusChangedPayload}</li>
 *   <li>{@code ORDER_CANCELLED}→ {@link OrderCancelledPayload} (includes reason)</li>
 * </ul>
 *
 * <p>TraceId and requestId are injected from MDC at message construction time
 * so the Kafka message carries the full observability context of the originating
 * HTTP request, enabling end-to-end log correlation across services.
 *
 * <p><b>Note on ORDER_CREATED payload:</b> The outbox entry does not store item
 * details (to keep the outbox table small). For ORDER_CREATED events, the relay
 * builds a minimal {@link OrderCreatedPayload} from what is in the outbox.
 * If full item details are needed in the event, the relay would need to fetch
 * the order — a deliberate trade-off documented here. For this implementation,
 * the payload carries status, and consumers call the API for item details.
 */
@Component
public class OrderEventMessageBuilder {

    public OrderEventMessage<?> build(OutboxEntry entry) {
        Object payload = buildPayload(entry);

        return new OrderEventMessage<>(
                UUID.fromString(entry.getEventId()),
                entry.getEventType(),
                1,
                entry.getOccurredAt(),
                entry.getOrderId(),
                MDC.get("traceId"),
                MDC.get("requestId"),
                payload
        );
    }

    private Object buildPayload(OutboxEntry entry) {
        return switch (entry.getEventType()) {
            case ORDER_CREATED -> new OrderCreatedPayload(
                    entry.getNewStatus(),
                    null,   // totalAmount not stored in outbox — consumers fetch from API
                    null,   // currency not stored in outbox
                    null    // items not stored in outbox (keeps outbox table small)
            );
            case ORDER_PAID, ORDER_FULFILLED -> new OrderStatusChangedPayload(
                    entry.getPreviousStatus(),
                    entry.getNewStatus()
            );
            case ORDER_CANCELLED -> new OrderCancelledPayload(
                    entry.getPreviousStatus(),
                    entry.getNewStatus(),
                    entry.getCancelReason()
            );
        };
    }
}
