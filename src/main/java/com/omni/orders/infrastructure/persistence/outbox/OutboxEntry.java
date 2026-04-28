package com.omni.orders.infrastructure.persistence.outbox;

import com.omni.orders.domain.model.OrderStatus;
import com.omni.orders.infrastructure.persistence.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the {@code order_outbox} table.
 *
 * <h3>Why minimal fields</h3>
 * The outbox stores only the data needed to construct the Kafka event message.
 * It does NOT store the full order. This is intentional:
 * <ul>
 *   <li>Keeps the outbox table small and fast to poll.</li>
 *   <li>Avoids serialising the entire order aggregate on every state change.</li>
 *   <li>Downstream consumers that need full order data call
 *       {@code GET /api/v1/orders/{id}} — the event is a trigger, not a data dump.</li>
 * </ul>
 *
 * <h3>Retry fields</h3>
 * {@code retryCount} tracks how many times the relay has attempted to publish.
 * Once it reaches {@code outbox.max-retries}, the entry is marked FAILED and
 * flagged for manual investigation.
 */
@Entity
@Table(
        name = "order_outbox"
)
@Getter
@Setter
@NoArgsConstructor
public class OutboxEntry extends BaseEntity {

    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 36)
    private String eventId;

    @Column(name = "order_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 30)
    private OrderEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private OrderStatus newStatus;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public OutboxEntry(UUID id,
                       String eventId,
                       UUID orderId,
                       OrderEventType eventType,
                       OrderStatus previousStatus,
                       OrderStatus newStatus,
                       String cancelReason,
                       Instant occurredAt) {
        super(id);
        this.eventId        = eventId;
        this.orderId        = orderId;
        this.eventType      = eventType;
        this.previousStatus = previousStatus;
        this.newStatus      = newStatus;
        this.cancelReason   = cancelReason;
        this.occurredAt     = occurredAt;
        this.status         = OutboxStatus.PENDING;
        this.retryCount     = 0;
    }
}
