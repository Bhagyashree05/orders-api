package com.omni.orders.api.dto.response;
import java.math.BigDecimal;
import java.util.UUID;
public record OrderItemResponse(UUID productId, String productName, int quantity,
                                 BigDecimal unitPrice, BigDecimal itemTotal) {}
