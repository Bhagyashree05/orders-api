package com.omni.orders.domain.model;

public enum OrderStatus {
    PENDING,
    PAID,
    FULFILLED,
    CANCELLED;

    public boolean isTerminal() {
        return this == FULFILLED || this == CANCELLED;
    }
}
