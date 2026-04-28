package com.omni.orders.domain.service;

import java.util.UUID;

public class OrderItemCommand {

    private final UUID productId;
    private final int  quantity;

    public OrderItemCommand(UUID productId, int quantity) {
        this.productId = productId;
        this.quantity  = quantity;
    }

    public UUID getProductId() { return productId; }
    public int  getQuantity()  { return quantity; }
}
