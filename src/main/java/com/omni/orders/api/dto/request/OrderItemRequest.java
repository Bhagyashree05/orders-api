package com.omni.orders.api.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
@Schema(description = "A single item in an order request")
public record OrderItemRequest(
        @NotNull(message = "productId is required") UUID productId,
        @Min(value = 1, message = "quantity must be at least 1") int quantity
) {}
