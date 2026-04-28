package com.omni.orders.domain.service;

import java.util.List;

public class PlaceOrderCommand {

    private final String                  idempotencyKey;
    private final List<OrderItemCommand>  items;

    public PlaceOrderCommand(String idempotencyKey, List<OrderItemCommand> items) {
        this.idempotencyKey = idempotencyKey;
        this.items          = List.copyOf(items);
    }

    public String                  getIdempotencyKey() { return idempotencyKey; }
    public List<OrderItemCommand>  getItems()          { return items; }
}
