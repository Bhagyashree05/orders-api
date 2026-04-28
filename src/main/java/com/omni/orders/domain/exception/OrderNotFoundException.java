package com.omni.orders.domain.exception;
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String msg) { super(msg); }
}
