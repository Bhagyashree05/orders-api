package com.omni.orders.api.controller;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders/{orderId}/fulfillments")
@Tag(name = "Fulfillments", description = "Mark orders as fulfilled by warehouse")
public class FulfillmentController {

    private final OrderApplicationService orderApplicationService;
    private final OrderApiMapper          orderApiMapper;

    public FulfillmentController(OrderApplicationService orderApplicationService,
                                  OrderApiMapper orderApiMapper) {
        this.orderApplicationService = orderApplicationService;
        this.orderApiMapper          = orderApiMapper;
    }

    @Operation(summary = "Mark order as fulfilled",
               description = "Transitions a PAID order to FULFILLED. Typically called by the WMS after shipment.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CustomerOrder fulfilled"),
            @ApiResponse(responseCode = "422", description = "CustomerOrder must be PAID first",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<OrderResponse> fulfillOrder(@PathVariable UUID orderId) {
        log.info("POST /orders/{}/fulfillments", orderId);
        return ResponseEntity.ok(orderApiMapper.toResponse(
                orderApplicationService.fulfillOrder(orderId)));
    }
}
