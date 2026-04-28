package com.omni.orders.infrastructure.persistence.entity;

import com.omni.orders.domain.model.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for orders table.
 * @Version for optimistic locking. idempotency_key UNIQUE for dedup.
 */
@Entity
@Table(name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_orders_idempotency_key", columnNames = "idempotency_key"))
@Getter @Setter @NoArgsConstructor
public class OrderEntity extends BaseEntity {

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    /** Never set manually — Hibernate manages @Version for optimistic locking CAS. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemEntity> items = new ArrayList<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private PaymentEntity payment;

    public OrderEntity(UUID id, String idempotencyKey, OrderStatus status) {
        super(id); this.idempotencyKey = idempotencyKey; this.status = status;
    }

    public void addItem(OrderItemEntity item) { item.setOrder(this); this.items.add(item); }
}
