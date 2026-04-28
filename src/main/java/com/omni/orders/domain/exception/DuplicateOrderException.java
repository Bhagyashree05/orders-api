package com.omni.orders.domain.exception;
public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String msg) { super(msg); }
}
