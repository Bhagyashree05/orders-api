package com.omni.orders.infrastructure.kafka.producer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Item detail within {@link OrderCreatedPayload}.
 */
public class OrderItemPayload {

    private final UUID       productId;
    private final int        quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal itemTotal;

    public OrderItemPayload(UUID productId, int quantity,
                             BigDecimal unitPrice, BigDecimal itemTotal) {
        this.productId = productId;
        this.quantity  = quantity;
        this.unitPrice = unitPrice;
        this.itemTotal = itemTotal;
    }

    public UUID       getProductId() { return productId; }
    public int        getQuantity()  { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getItemTotal() { return itemTotal; }
}
