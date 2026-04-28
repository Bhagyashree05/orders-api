package com.omni.orders.api.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.omni.orders.domain.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(UUID id, String idempotencyKey, OrderStatus status, BigDecimal totalAmount,
                             List<OrderItemResponse> items, PaymentResponse payment, long version,
                             Instant createdAt, Instant updatedAt, String cancelReason) {}
