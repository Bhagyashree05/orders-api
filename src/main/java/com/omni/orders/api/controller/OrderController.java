package com.omni.orders.api.controller;

import com.omni.orders.api.dto.request.CancelOrderRequest;
import com.omni.orders.api.dto.request.CreateOrderRequest;
import com.omni.orders.api.dto.response.ErrorResponse;
import com.omni.orders.api.dto.response.OrderResponse;
import com.omni.orders.api.mapper.OrderApiMapper;
import com.omni.orders.domain.model.CustomerOrder;
import com.omni.orders.domain.service.OrderApplicationService;
import com.omni.orders.domain.service.PlaceOrderCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders", description = "Create and manage orders")
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderApiMapper          orderApiMapper;

    public OrderController(OrderApplicationService orderApplicationService,
                            OrderApiMapper orderApiMapper) {
        this.orderApplicationService = orderApplicationService;
        this.orderApiMapper          = orderApiMapper;
    }

    @Operation(summary = "Create a new order",
               description = "Creates an order in PENDING status. idempotencyKey prevents duplicates — same key returns 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "CustomerOrder created"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Duplicate idempotency key",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /orders idempotencyKey={}", request.idempotencyKey());
        PlaceOrderCommand command = new PlaceOrderCommand(
                request.idempotencyKey(),
                orderApiMapper.toOrderItemCommands(request.items()));
        CustomerOrder order = orderApplicationService.placeOrder(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderApiMapper.toResponse(order));
    }

    @Operation(summary = "Get order by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CustomerOrder found"),
            @ApiResponse(responseCode = "404", description = "CustomerOrder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderApiMapper.toResponse(orderApplicationService.getOrder(id)));
    }

    @Operation(summary = "Cancel an order",
               description = "Cancels a PENDING or PAID order. FULFILLED orders cannot be cancelled.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CustomerOrder cancelled"),
            @ApiResponse(responseCode = "404", description = "CustomerOrder not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Invalid state transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id,
                                                      @Valid @RequestBody CancelOrderRequest request) {
        log.info("POST /orders/{}/cancel", id);
        return ResponseEntity.ok(orderApiMapper.toResponse(
                orderApplicationService.cancelOrder(id, request.reason())));
    }
}
