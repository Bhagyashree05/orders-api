package com.omni.orders.domain.exception;
import java.math.BigDecimal;
public class PaymentAmountMismatchException extends RuntimeException {
    public PaymentAmountMismatchException(BigDecimal expected, BigDecimal actual) {
        super("Payment amount %s does not match order total %s".formatted(actual, expected));
    }
}
