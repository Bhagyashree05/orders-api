package com.omni.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Separate @Entity for payments. FK on payments side — inserting payment never touches orders row. */
@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor
public class PaymentEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_payments_order_id"))
    private OrderEntity order;

    @Column(name = "mode",            nullable = false, length = 20)  private String     mode;
    @Column(name = "transaction_id",  nullable = false, length = 128) private String     transactionId;
    @Column(name = "amount",          nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(name = "currency",        nullable = false, length = 3)   private String     currency;
    @Column(name = "paid_at",         nullable = false)                private Instant    paidAt;

    public PaymentEntity(UUID id, OrderEntity order, String mode, String transactionId,
                         BigDecimal amount, String currency, Instant paidAt) {
        super(id);
        this.order = order; this.mode = mode; this.transactionId = transactionId;
        this.amount = amount; this.currency = currency; this.paidAt = paidAt;
    }
}
