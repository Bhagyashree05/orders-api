package com.omni.orders.api.dto.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
@Schema(description = "Request to cancel an order")
public record CancelOrderRequest(
        @NotBlank(message = "reason must not be blank") @Size(max = 500) String reason
) {}
