package com.omni.orders.api.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
@Schema(description = "Request to process payment for an order")
public record ProcessPaymentRequest(
        @NotNull(message = "paymentMode is required") PaymentMode paymentMode,
        @NotBlank(message = "transactionId is required") @Size(max = 128) String transactionId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3, message = "currency must be 3-letter ISO-4217") String currency,
        Instant paidAt
) {}
