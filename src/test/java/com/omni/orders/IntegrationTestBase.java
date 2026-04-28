package com.omni.orders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.orders.api.dto.request.CreateOrderRequest;
import com.omni.orders.api.dto.request.OrderItemRequest;
import com.omni.orders.api.dto.request.PaymentMode;
import com.omni.orders.api.dto.request.ProcessPaymentRequest;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared Testcontainers base for all integration and E2E tests.
 *
 * <p>Containers are declared as {@code static} fields — started once per JVM
 * and shared across all test classes. This is the key optimisation: containers
 * start in ~15s the first time; subsequent test classes reuse the same instance.
 *
 * <p>Background scheduling is disabled in tests via {@code spring.scheduling.enabled=false}
 * in {@code src/test/resources/application.yml}. This prevents the
 * {@link com.omni.orders.infrastructure.persistence.outbox.OutboxRelayScheduler}
 * from polling the outbox in the background, which would exhaust HikariCP connections
 * when Testcontainers recycles Postgres containers between test classes.
 * {@code KafkaOutboxRelayIT} calls {@code relay()} directly for controlled testing.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTestBase {

    /** SKU-001 Wireless Headphones @ 49.99 — matches V2__seed_products.sql */
    protected static final UUID PRODUCT_SKU001 =
            UUID.fromString("3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6");

    /** SKU-002 USB-C Hub @ 29.99 — matches V2__seed_products.sql */
    protected static final UUID PRODUCT_SKU002 =
            UUID.fromString("b1c2d3e4-f5a6-3b7c-8d9e-0a1b2c3d4e5f");

    /** 2 × SKU-001 @ 49.99 = 99.98 */
    protected static final BigDecimal ORDER_TOTAL_2X_SKU001 = new BigDecimal("99.98");

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("ordersdb")
                    .withUsername("orders")
                    .withPassword("orders");

    @Container
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",         POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username",    POSTGRES::getUsername);
        r.add("spring.datasource.password",    POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    // ── Helper methods ────────────────────────────────────────────────────────

    protected String createOrderAndGetId(MockMvc mockMvc, ObjectMapper om) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildCreateJson(om)))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    protected String buildCreateJson(ObjectMapper om) throws Exception {
        return om.writeValueAsString(new CreateOrderRequest(
                "idem-" + UUID.randomUUID(),
                List.of(new OrderItemRequest(PRODUCT_SKU001, 2))));
    }

    protected ProcessPaymentRequest buildPaymentRequest(BigDecimal amount) {
        return new ProcessPaymentRequest(PaymentMode.CARD, "txn-" + UUID.randomUUID(),
                amount, "EUR", null);
    }

}
