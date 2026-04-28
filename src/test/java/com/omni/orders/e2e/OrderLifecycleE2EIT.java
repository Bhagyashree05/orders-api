package com.omni.orders.e2e;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end scenario tests covering the full stack:
 * HTTP → OrderApplicationService → Postgres → order_outbox → (relay) → Kafka.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("E2E: CustomerOrder lifecycle scenarios")
class OrderLifecycleE2EIT extends IntegrationTestBase {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @DisplayName("Scenario 1 — Happy path: PENDING → PAID → FULFILLED, version 0→1→2")
    void happy_path_full_lifecycle() throws Exception {
        // Create
        MvcResult createResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildCreateJson(objectMapper)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode created = parse(createResult);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.get("version").asLong()).isZero();
        assertThat(created.get("totalAmount").asText()).isEqualTo("99.98");
        assertThat(created.path("payment").isMissingNode()).isTrue();

        // Pay
        MvcResult payResult = mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isOk()).andReturn();

        JsonNode paid = parse(payResult);
        assertThat(paid.get("status").asText()).isEqualTo("PAID");
        assertThat(paid.get("version").asLong()).isEqualTo(1L);
        assertThat(paid.at("/payment/paymentMode").asText()).isEqualTo("CARD");
        assertThat(paid.at("/payment/currency").asText()).isEqualTo("EUR");
        assertThat(paid.at("/payment/amount").asDouble()).isEqualTo(99.98);

        // Fulfill
        MvcResult fulfillResult = mockMvc.perform(
                        post("/api/v1/orders/{id}/fulfillments", id))
                .andExpect(status().isOk()).andReturn();

        JsonNode fulfilled = parse(fulfillResult);
        assertThat(fulfilled.get("status").asText()).isEqualTo("FULFILLED");
        assertThat(fulfilled.get("version").asLong()).isEqualTo(2L);

        // GET reflects persisted final state
        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULFILLED"))
                .andExpect(jsonPath("$.payment.paymentMode").value("CARD"))
                .andExpect(jsonPath("$.items[0].productName").value("Wireless Headphones"));

        // Terminal — cannot pay again
        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("Scenario 2 — Cancel after payment: PENDING→PAID→CANCELLED, payment data preserved")
    void cancel_after_payment_preserves_payment_data() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CancelOrderRequest("fraud detected"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("fraud detected"))
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.payment.paymentMode").value("CARD")); // preserved for audit

        // Cannot pay after cancel
        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("Scenario 3 — Idempotent create: same key twice returns 409, original order unchanged")
    void idempotent_create_rejects_duplicate() throws Exception {
        String key  = "e2e-idem-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(
                key, List.of(new OrderItemRequest(PRODUCT_SKU001, 1))));

        MvcResult first = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_ORDER"));

        // Original still accessible and unchanged
        String id = parse(first).get("id").asText();
        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Scenario 4 — Payment amount mismatch: order remains PENDING at version 0")
    void payment_mismatch_leaves_order_unchanged() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(new BigDecimal("0.01")))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PAYMENT_AMOUNT_MISMATCH"));

        mockMvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    @DisplayName("Scenario 5 — Error responses always include traceId and timestamp")
    void error_responses_contain_trace_context() throws Exception {
        mockMvc.perform(get("/api/v1/orders/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Scenario 6 — Multi-SKU order: two items, correct total (49.99 + 2×29.99 = 109.97)")
    void multi_sku_order_totals_correctly() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateOrderRequest(
                "multi-" + UUID.randomUUID(),
                List.of(
                        new OrderItemRequest(PRODUCT_SKU001, 1),   // 49.99
                        new OrderItemRequest(PRODUCT_SKU002, 2)    // 2 × 29.99 = 59.98
                )
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalAmount").value(109.97));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
