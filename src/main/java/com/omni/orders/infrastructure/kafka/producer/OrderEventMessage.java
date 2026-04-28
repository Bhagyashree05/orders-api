package com.omni.orders.infrastructure.kafka.producer;

import com.omni.orders.infrastructure.persistence.outbox.OrderEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic Kafka event envelope for all order domain events.
 *
 * <p>The generic type {@code T} is the event-specific payload. This design:
 * <ul>
 *   <li>Keeps all events on one topic ({@code order.events}) — consumers
 *       subscribe once and branch on {@code eventType}.</li>
 *   <li>Separates common metadata (eventId, traceId, requestId, occurredAt)
 *       from event-specific data (the typed payload).</li>
 *   <li>Enables schema evolution per event type independently — adding a field
 *       to {@link OrderCreatedPayload} does not affect {@link OrderStatusChangedPayload}.</li>
 * </ul>
 *
 * <h3>Consumer dedup</h3>
 * {@code eventId} is a UUID unique per published event. Consumers store
 * processed {@code eventId} values to detect and discard duplicates from
 * at-least-once delivery.
 *
 * <h3>Trace propagation</h3>
 * {@code traceId} and {@code requestId} are injected from MDC at publish time
 * so consumers can correlate their log lines with the originating HTTP request
 * in Zipkin.
 *
 * @param <T> typed payload — one of:
 *            {@link OrderCreatedPayload},
 *            {@link OrderStatusChangedPayload},
 *            {@link OrderCancelledPayload}
 */
public class OrderEventMessage<T> {

    private final UUID           eventId;
    private final OrderEventType eventType;
    private final int            schemaVersion;
    private final Instant        occurredAt;
    private final UUID           orderId;
    private final String         traceId;
    private final String         requestId;
    private final T              payload;

    public OrderEventMessage(UUID eventId,
                             OrderEventType eventType,
                             int schemaVersion,
                             Instant occurredAt,
                             UUID orderId,
                             String traceId,
                             String requestId,
                             T payload) {
        this.eventId       = eventId;
        this.eventType     = eventType;
        this.schemaVersion = schemaVersion;
        this.occurredAt    = occurredAt;
        this.orderId       = orderId;
        this.traceId       = traceId;
        this.requestId     = requestId;
        this.payload       = payload;
    }

    public UUID           getEventId()       { return eventId; }
    public OrderEventType getEventType()     { return eventType; }
    public int            getSchemaVersion() { return schemaVersion; }
    public Instant        getOccurredAt()    { return occurredAt; }
    public UUID           getOrderId()       { return orderId; }
    public String         getTraceId()       { return traceId; }
    public String         getRequestId()     { return requestId; }
    public T              getPayload()       { return payload; }
}
