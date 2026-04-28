package com.omni.orders.domain.exception;
public class OptimisticLockException extends RuntimeException {
    public OptimisticLockException(String msg) { super(msg); }
}
