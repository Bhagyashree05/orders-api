package com.omni.orders.api.dto.response;
import com.omni.orders.api.dto.request.PaymentMode;
import java.math.BigDecimal;
import java.time.Instant;
public record PaymentResponse(PaymentMode paymentMode, String transactionId,
                               BigDecimal amount, String currency, Instant paidAt) {}
