package com.omni.orders.domain.exception;
import com.omni.orders.domain.model.OrderStatus;
public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from %s to %s".formatted(from, to));
    }
}
