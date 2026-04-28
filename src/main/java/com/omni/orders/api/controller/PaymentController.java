package com.omni.orders.api.controller;

import com.omni.orders.api.dto.request.ProcessPaymentRequest;
import com.omni.orders.api.dto.response.ErrorResponse;
import com.omni.orders.api.dto.response.OrderResponse;
import com.omni.orders.api.mapper.OrderApiMapper;
import com.omni.orders.domain.service.OrderApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders/{orderId}/payments")
@Tag(name = "Payments", description = "Process payments for orders")
public class PaymentController {

    private final OrderApplicationService orderApplicationService;
    private final OrderApiMapper          orderApiMapper;

    public PaymentController(OrderApplicationService orderApplicationService,
                              OrderApiMapper orderApiMapper) {
        this.orderApplicationService = orderApplicationService;
        this.orderApiMapper          = orderApiMapper;
    }

    @Operation(summary = "Process payment for an order",
               description = "Transitions a PENDING order to PAID. Amount must exactly match the order total.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment processed, order is now PAID"),
            @ApiResponse(responseCode = "404", description = "CustomerOrder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent modification",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Invalid transition or amount mismatch",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OrderResponse> processPayment(@PathVariable UUID orderId,
                                                         @Valid @RequestBody ProcessPaymentRequest request) {
        log.info("POST /orders/{}/payments mode={}", orderId, request.paymentMode());
        return ResponseEntity.ok(orderApiMapper.toResponse(
                orderApplicationService.processPayment(orderId, orderApiMapper.toPaymentCommand(request))));
    }
}
