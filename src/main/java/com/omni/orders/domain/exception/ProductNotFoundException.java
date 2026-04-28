package com.omni.orders.domain.exception;
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String msg) { super(msg); }
}
