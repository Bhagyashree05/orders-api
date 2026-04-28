package com.omni.orders.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * CustomerOrder aggregate root — intentionally immutable.
 *
 * <p>Every state transition returns a new {@code CustomerOrder} instance via the
 * named factory methods. The original instance is never mutated, which makes
 * concurrent reads safe and the state machine trivially testable.
 *
 * <h3>State machine</h3>
 * <pre>
 *  PENDING ──► PAID ──► FULFILLED
 *     │          │
 *     └──────────┴──► CANCELLED
 * </pre>
 *
 * <h3>Optimistic concurrency</h3>
 * {@code version} mirrors Hibernate's {@code @Version} column.
 * Every transition increments it. The JPA adapter enforces the CAS at DB level.
 */
public class CustomerOrder {

    private final UUID          id;
    private final String        idempotencyKey;
    private final List<OrderItem> items;
    private final OrderStatus   status;
    private final Payment       payment;
    private final long          version;
    private final Instant       createdAt;
    private final Instant       updatedAt;
    private final String        cancelReason;

    public CustomerOrder(UUID id,
                 String idempotencyKey,
                 List<OrderItem> items,
                 OrderStatus status,
                 Payment payment,
                 long version,
                 Instant createdAt,
                 Instant updatedAt,
                 String cancelReason) {
        this.id             = id;
        this.idempotencyKey = idempotencyKey;
        this.items          = Collections.unmodifiableList(items);
        this.status         = status;
        this.payment        = payment;
        this.version        = version;
        this.createdAt      = createdAt;
        this.updatedAt      = updatedAt;
        this.cancelReason   = cancelReason;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID            getId()             { return id; }
    public String          getIdempotencyKey() { return idempotencyKey; }
    public List<OrderItem> getItems()          { return items; }
    public OrderStatus     getStatus()         { return status; }
    public Payment         getPayment()        { return payment; }
    public long            getVersion()        { return version; }
    public Instant         getCreatedAt()      { return createdAt; }
    public Instant         getUpdatedAt()      { return updatedAt; }
    public String          getCancelReason()   { return cancelReason; }

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(OrderItem::getItemTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── State machine guard ───────────────────────────────────────────────────

    /**
     * Pure guard — no side effects. Returns true if the requested transition
     * is allowed from the current status.
     */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this.status) {
            case PENDING             -> next == OrderStatus.PAID      || next == OrderStatus.CANCELLED;
            case PAID                -> next == OrderStatus.FULFILLED || next == OrderStatus.CANCELLED;
            case FULFILLED, CANCELLED -> false;
        };
    }

    // ── Transition factories ──────────────────────────────────────────────────

    public CustomerOrder withPayment(Payment payment, long newVersion) {
        return new CustomerOrder(id, idempotencyKey, items,
                OrderStatus.PAID, payment,
                newVersion, createdAt, Instant.now(), cancelReason);
    }

    public CustomerOrder withCancellation(String reason, long newVersion) {
        return new CustomerOrder(id, idempotencyKey, items,
                OrderStatus.CANCELLED, payment,
                newVersion, createdAt, Instant.now(), reason);
    }

    public CustomerOrder withStatus(OrderStatus newStatus, long newVersion) {
        return new CustomerOrder(id, idempotencyKey, items,
                newStatus, payment,
                newVersion, createdAt, Instant.now(), cancelReason);
    }
}
