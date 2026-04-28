package com.omni.orders.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.orders.IntegrationTestBase;
import com.omni.orders.api.dto.request.CancelOrderRequest;
import com.omni.orders.api.dto.request.CreateOrderRequest;
import com.omni.orders.api.dto.request.OrderItemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the CustomerOrder API.
 * Full Spring context with real Postgres and Kafka via Testcontainers.
 * Flyway runs V1__schema + V2__seed_products before the first test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("OrderController integration tests")
class OrderControllerIT extends IntegrationTestBase {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @Test
    @DisplayName("GET /products — returns 5 seeded products from V2 migration")
    void list_products_returns_seeded_catalog() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.sku == 'SKU-001')].name",
                        hasItem("Wireless Headphones")))
                .andExpect(jsonPath("$[?(@.sku == 'SKU-001')].price",
                        hasItem(49.99)));
    }

    @Test
    @DisplayName("POST /orders — 201 Created, status PENDING, version 0, payment absent")
    void create_order_returns_201() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildCreateJson(objectMapper)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.totalAmount").value(99.98))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Wireless Headphones"))
                .andExpect(jsonPath("$.items[0].itemTotal").value(99.98))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    @DisplayName("POST /orders — duplicate idempotencyKey returns 409 DUPLICATE_ORDER")
    void duplicate_idempotency_key_returns_conflict() throws Exception {
        String fixedKey = "fixed-key-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(
                fixedKey, List.of(new OrderItemRequest(PRODUCT_SKU001, 1))));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_ORDER"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /orders — unknown productId returns 422 PRODUCT_NOT_FOUND")
    void unknown_product_returns_422() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(
                "idem-" + UUID.randomUUID(),
                List.of(new OrderItemRequest(UUID.randomUUID(), 1))));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /orders — blank idempotencyKey returns 400 VALIDATION_ERROR with field details")
    void blank_idempotency_key_returns_400() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(
                "", List.of(new OrderItemRequest(PRODUCT_SKU001, 1))));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasItem(containsString("idempotencyKey"))));
    }

    @Test
    @DisplayName("GET /orders/{id} — returns full order with items")
    void get_order_returns_correct_data() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("GET /orders/{unknown} — 404 NOT_FOUND")
    void get_unknown_order_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /{id}/payments — correct amount transitions to PAID with payment block")
    void pay_order_returns_paid_with_payment_block() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.payment.paymentMode").value("CARD"))
                .andExpect(jsonPath("$.payment.currency").value("EUR"))
                .andExpect(jsonPath("$.payment.amount").value(99.98))
                .andExpect(jsonPath("$.payment.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.payment.paidAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /{id}/payments — wrong amount returns 422 PAYMENT_AMOUNT_MISMATCH")
    void wrong_payment_amount_returns_422() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(new BigDecimal("1.00")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    @DisplayName("POST /{id}/cancel — PENDING → CANCELLED with reason persisted")
    void cancel_pending_order() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CancelOrderRequest("customer request"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("customer request"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("POST /{id}/cancel — blank reason returns 400")
    void cancel_blank_reason_returns_400() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CancelOrderRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Full lifecycle: PENDING→PAID→FULFILLED, version 0→1→2, terminal blocks further transitions")
    void full_lifecycle_happy_path() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        // Pay
        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Fulfill
        mockMvc.perform(post("/api/v1/orders/{id}/fulfillments", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"))
                .andExpect(jsonPath("$.version").value(2));

        // Verify GET reflects persisted state including payment
        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(jsonPath("$.status").value("FULFILLED"))
                .andExpect(jsonPath("$.payment.paymentMode").value("CARD"));

        // Terminal: cannot pay again
        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
    }
}
