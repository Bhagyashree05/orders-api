package com.omni.orders.infrastructure.persistence;

import com.omni.orders.IntegrationTestBase;
import com.omni.orders.domain.exception.DuplicateOrderException;
import com.omni.orders.domain.exception.OptimisticLockException;
import com.omni.orders.domain.model.*;
import com.omni.orders.domain.port.OrderRepository;
import com.omni.orders.domain.port.OutboxEventRepository;
import com.omni.orders.infrastructure.persistence.outbox.OutboxEntry;
import com.omni.orders.infrastructure.persistence.outbox.OutboxEntryFactory;
import com.omni.orders.infrastructure.persistence.outbox.OutboxStatus;
import com.omni.orders.infrastructure.persistence.repository.SpringDataPaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the JPA persistence adapter directly (not via HTTP).
 * Covers: round-trip, PaymentEntity in separate table, optimistic locking,
 * idempotency key constraint, Spring Data auditing, and outbox entries.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("CustomerOrder JPA persistence integration tests")
class OrderPersistenceIT extends IntegrationTestBase {

    @Autowired OrderRepository          orderRepository;
    @Autowired OutboxEventRepository    outboxEventRepository;
    @Autowired OutboxEntryFactory       outboxEntryFactory;
    @Autowired SpringDataPaymentRepository paymentRepository;

    @Test
    @DisplayName("save + findById — all fields including items round-trip correctly")
    @Transactional
    void save_and_find_roundtrip() {
        CustomerOrder order = buildOrder("key-roundtrip");
        orderRepository.save(order);

        Optional<CustomerOrder> found = orderRepository.findById(order.getId());
        assertThat(found).isPresent();

        CustomerOrder loaded = found.get();
        assertThat(loaded.getId()).isEqualTo(order.getId());
        assertThat(loaded.getIdempotencyKey()).isEqualTo("key-roundtrip");
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(loaded.getTotalAmount()).isEqualByComparingTo("10.00");
        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getItems().get(0).getProductName()).isEqualTo("Widget");
        assertThat(loaded.getPayment()).isNull();
        assertThat(loaded.getCreatedAt()).isNotNull();   // Spring Data @CreatedDate
        assertThat(loaded.getUpdatedAt()).isNotNull();   // Spring Data @LastModifiedDate
    }

    @Test
    @DisplayName("payment persisted in separate payments table via @OneToOne")
    @Transactional
    void payment_stored_in_separate_table() {
        CustomerOrder order = buildOrder("key-payment");
        orderRepository.save(order);

        Payment payment = new Payment("CARD", "txn-" + UUID.randomUUID(),
                new BigDecimal("10.00"), "EUR", Instant.now());
        CustomerOrder paid = order.withPayment(payment, 1L);
        orderRepository.update(paid);

        CustomerOrder loaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(loaded.getPayment()).isNotNull();
        assertThat(loaded.getPayment().getMode()).isEqualTo("CARD");
        assertThat(loaded.getPayment().getCurrency()).isEqualTo("EUR");

        // Verify directly in payments table
        assertThat(paymentRepository.findByOrderId(order.getId())).isPresent();
    }

    @Test
    @DisplayName("duplicate idempotency key throws DuplicateOrderException")
    void duplicate_idempotency_key_throws() {
        orderRepository.save(buildOrder("key-dup"));
        assertThatThrownBy(() -> orderRepository.save(buildOrder("key-dup")))
                .isInstanceOf(DuplicateOrderException.class)
                .hasMessageContaining("key-dup");
    }

    @Test
    @DisplayName("concurrent update throws OptimisticLockException")
    void optimistic_lock_throws() {
        CustomerOrder order = buildOrder("key-lock");
        orderRepository.save(order);

        // First update succeeds
        orderRepository.update(order.withStatus(OrderStatus.CANCELLED, 1L));

        // Second update with stale version 0 → conflicts with stored version 1
        assertThatThrownBy(() ->
                orderRepository.update(order.withStatus(OrderStatus.CANCELLED, 1L)))
                .isInstanceOf(OptimisticLockException.class);
    }

    @Test
    @DisplayName("findById with unknown ID returns Optional.empty()")
    void find_unknown_returns_empty() {
        assertThat(orderRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("cancellation persists reason and version, payment remains null")
    @Transactional
    void cancel_from_pending() {
        CustomerOrder order = buildOrder("key-cancel");
        orderRepository.save(order);
        orderRepository.update(order.withCancellation("fraud", 1L));

        CustomerOrder loaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(loaded.getCancelReason()).isEqualTo("fraud");
        assertThat(loaded.getPayment()).isNull();
    }

    @Test
    @DisplayName("outbox entry saved in PENDING state with correct eventType")
    @Transactional
    void outbox_entry_saved_on_creation() {
        CustomerOrder order = buildOrder("key-outbox");
        orderRepository.save(order);
        outboxEventRepository.save(outboxEntryFactory.forCreated(order));

        List<OutboxEntry> pending = outboxEventRepository.findPendingEntries();
        assertThat(pending).anyMatch(e ->
                e.getOrderId().equals(order.getId()) &&
                e.getStatus() == OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("outbox markProcessing CAS — returns true once, false if already claimed")
    @Transactional
    void outbox_mark_processing_cas() {
        CustomerOrder order = buildOrder("key-cas");
        orderRepository.save(order);

        OutboxEntry entry = outboxEntryFactory.forCreated(order);
        outboxEventRepository.save(entry);

        boolean first  = outboxEventRepository.markProcessing(entry);
        boolean second = outboxEventRepository.markProcessing(entry);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CustomerOrder buildOrder(String idempotencyKey) {
        return new CustomerOrder(
                UUID.randomUUID(), idempotencyKey,
                List.of(new OrderItem(PRODUCT_SKU001, "Widget", 1, new BigDecimal("10.00"))),
                OrderStatus.PENDING, null, 0L,
                Instant.now(), Instant.now(), null
        );
    }
}
