package com.omni.orders.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Immutable item within an order.
 *
 * <p>"Item" rather than "line" — item is customer vocabulary;
 * line item is accounting vocabulary.
 *
 * <p>Price is snapshotted from the catalog at order-creation time
 * so future catalog changes never affect historical orders.
 */
public class OrderItem {

    private final UUID       productId;
    private final String     productName;
    private final int        quantity;
    private final BigDecimal unitPrice;

    public OrderItem(UUID productId, String productName,
                     int quantity, BigDecimal unitPrice) {
        if (quantity < 1)      throw new IllegalArgumentException("quantity must be >= 1");
        if (unitPrice == null) throw new IllegalArgumentException("unitPrice must not be null");
        this.productId   = productId;
        this.productName = productName;
        this.quantity    = quantity;
        this.unitPrice   = unitPrice;
    }

    public UUID       getProductId()   { return productId; }
    public String     getProductName() { return productName; }
    public int        getQuantity()    { return quantity; }
    public BigDecimal getUnitPrice()   { return unitPrice; }

    public BigDecimal getItemTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
