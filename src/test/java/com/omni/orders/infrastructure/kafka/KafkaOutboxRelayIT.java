package com.omni.orders.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.orders.IntegrationTestBase;
import com.omni.orders.api.dto.request.CancelOrderRequest;
import com.omni.orders.infrastructure.persistence.outbox.OutboxRelayScheduler;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the full outbox relay flow:
 * HTTP → service → DB (order + outbox entry) → relay → Kafka.
 *
 * The scheduler is disabled in test/application.yml to prevent it from firing
 * before Testcontainers has finished starting Postgres. Instead, each test
 * calls {@link OutboxRelayScheduler#relay()} directly after the HTTP request
 * so we control exactly when publishing happens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@DisplayName("Kafka outbox relay integration tests")
class KafkaOutboxRelayIT extends IntegrationTestBase {

    @Autowired MockMvc               mockMvc;
    @Autowired ObjectMapper          objectMapper;
    @Autowired OutboxRelayScheduler  outboxRelayScheduler;

    private KafkaConsumer<String, String> consumer;
    private final List<String> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setupConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                 "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,        "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("order.events"));
        consumer.poll(Duration.ofMillis(500)); // trigger partition assignment
    }

    @AfterEach
    void teardown() {
        consumer.close();
        received.clear();
    }

    @Test
    @DisplayName("ORDER_CREATED — envelope has eventType, orderId, schemaVersion=1")
    void order_created_event_published() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildCreateJson(objectMapper)))
                .andExpect(status().isCreated());

        outboxRelayScheduler.relay(); // trigger manually — scheduler is disabled in tests

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            poll();
            assertThat(received).anyMatch(v -> {
                try {
                    JsonNode n = objectMapper.readTree(v);
                    return "ORDER_CREATED".equals(n.path("eventType").asText())
                            && n.path("schemaVersion").asInt() == 1
                            && !n.path("orderId").asText().isBlank()
                            && !n.path("eventId").asText().isBlank();
                } catch (Exception e) { return false; }
            });
        });
    }

    @Test
    @DisplayName("ORDER_PAID — payload has previousStatus=PENDING, newStatus=PAID")
    void order_paid_event_has_correct_payload() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isOk());

        outboxRelayScheduler.relay();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            poll();
            assertThat(received).anyMatch(v -> {
                try {
                    JsonNode n = objectMapper.readTree(v);
                    JsonNode payload = n.path("payload");
                    return "ORDER_PAID".equals(n.path("eventType").asText())
                            && "PENDING".equals(payload.path("previousStatus").asText())
                            && "PAID".equals(payload.path("newStatus").asText());
                } catch (Exception e) { return false; }
            });
        });
    }

    @Test
    @DisplayName("ORDER_CANCELLED — payload has reason")
    void order_cancelled_event_has_reason() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/cancel", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CancelOrderRequest("test cancellation"))))
                .andExpect(status().isOk());

        outboxRelayScheduler.relay();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            poll();
            assertThat(received).anyMatch(v -> {
                try {
                    JsonNode n = objectMapper.readTree(v);
                    JsonNode payload = n.path("payload");
                    return "ORDER_CANCELLED".equals(n.path("eventType").asText())
                            && "test cancellation".equals(payload.path("reason").asText());
                } catch (Exception e) { return false; }
            });
        });
    }

    @Test
    @DisplayName("ORDER_FULFILLED — payload has newStatus=FULFILLED")
    void order_fulfilled_event_published() throws Exception {
        String id = createOrderAndGetId(mockMvc, objectMapper);

        mockMvc.perform(post("/api/v1/orders/{id}/payments", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildPaymentRequest(ORDER_TOTAL_2X_SKU001))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders/{id}/fulfillments", id))
                .andExpect(status().isOk());

        outboxRelayScheduler.relay();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            poll();
            assertThat(received).anyMatch(v -> {
                try {
                    JsonNode n = objectMapper.readTree(v);
                    JsonNode payload = n.path("payload");
                    return "ORDER_FULFILLED".equals(n.path("eventType").asText())
                            && "FULFILLED".equals(payload.path("newStatus").asText());
                } catch (Exception e) { return false; }
            });
        });
    }

    @Test
    @DisplayName("Event envelope contains traceId and requestId fields")
    void event_envelope_contains_trace_context() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildCreateJson(objectMapper)))
                .andExpect(status().isCreated());

        outboxRelayScheduler.relay();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            poll();
            assertThat(received).anyMatch(v -> {
                try {
                    JsonNode n = objectMapper.readTree(v);
                    return "ORDER_CREATED".equals(n.path("eventType").asText())
                            && n.has("traceId")
                            && n.has("requestId");
                } catch (Exception e) { return false; }
            });
        });
    }

    private void poll() {
        consumer.poll(Duration.ofMillis(500))
                .forEach((ConsumerRecord<String, String> r) -> received.add(r.value()));
    }
}
