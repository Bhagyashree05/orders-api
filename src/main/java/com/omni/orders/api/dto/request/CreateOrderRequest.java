package com.omni.orders.api.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
@Schema(description = "Request to create a new order")
public record CreateOrderRequest(
        @NotBlank(message = "idempotencyKey must not be blank") @Size(max = 128) String idempotencyKey,
        @NotEmpty(message = "At least one item is required") @Valid List<OrderItemRequest> items
) {}
