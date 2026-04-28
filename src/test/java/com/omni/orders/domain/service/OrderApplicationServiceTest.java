package com.omni.orders.domain.service;

import com.omni.orders.domain.exception.*;
import com.omni.orders.domain.model.*;
import com.omni.orders.domain.port.OrderRepository;
import com.omni.orders.domain.port.OutboxEventRepository;
import com.omni.orders.domain.port.ProductRepository;
import com.omni.orders.infrastructure.persistence.outbox.OutboxEntryFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderApplicationService unit tests")
class OrderApplicationServiceTest {

    @Mock OrderRepository       orderRepository;
    @Mock ProductRepository     productRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock OutboxEntryFactory    outboxEntryFactory;

    @InjectMocks OrderApplicationServiceImpl service;

    private static final UUID    PID     = UUID.randomUUID();
    private static final Product PRODUCT = new Product(PID, "SKU-001", "Widget", new BigDecimal("10.00"));

    @Nested @DisplayName("placeOrder")
    class PlaceOrder {

        @Test @DisplayName("creates PENDING order and saves an outbox entry")
        void success() {
            when(productRepository.findById(PID)).thenReturn(Optional.of(PRODUCT));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            // outboxEntryFactory.forCreated() returns null by default from Mockito — that's fine.
            // The service only passes the result to outboxEventRepository.save(); we verify that below.

            CustomerOrder order = service.placeOrder(
                    new PlaceOrderCommand("key-1", List.of(new OrderItemCommand(PID, 2))));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getTotalAmount()).isEqualByComparingTo("20.00");
            assertThat(order.getVersion()).isZero();
            verify(outboxEntryFactory).forCreated(any());
            verify(outboxEventRepository).save(any());
        }

        @Test @DisplayName("throws ProductNotFoundException for unknown product")
        void unknown_product() {
            when(productRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.placeOrder(
                    new PlaceOrderCommand("k", List.of(new OrderItemCommand(UUID.randomUUID(), 1)))))
                    .isInstanceOf(ProductNotFoundException.class);

            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("processPayment")
    class ProcessPayment {

        @Test @DisplayName("PENDING → PAID with payment attached, outbox entry saved")
        void success() {
            CustomerOrder pending = buildOrder(OrderStatus.PENDING, 0L);
            when(orderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(orderRepository.update(any())).thenAnswer(i -> i.getArgument(0));

            CustomerOrder paid = service.processPayment(pending.getId(),
                    new ProcessPaymentCommand("CARD", "txn_1",
                            new BigDecimal("10.00"), "EUR", Instant.now()));

            assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(paid.getPayment()).isNotNull();
            assertThat(paid.getPayment().getTransactionId()).isEqualTo("txn_1");
            assertThat(paid.getVersion()).isEqualTo(1L);
            verify(outboxEntryFactory).forPaid(any(), any());
            verify(outboxEventRepository).save(any());
        }

        @Test @DisplayName("rejects payment when amount does not match order total")
        void amount_mismatch() {
            CustomerOrder pending = buildOrder(OrderStatus.PENDING, 0L);
            when(orderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.processPayment(pending.getId(),
                    new ProcessPaymentCommand("CARD", "txn", new BigDecimal("5.00"), "EUR", null)))
                    .isInstanceOf(PaymentAmountMismatchException.class);

            verify(outboxEventRepository, never()).save(any());
        }

        @Test @DisplayName("FULFILLED order cannot be paid")
        void invalid_transition() {
            CustomerOrder fulfilled = buildOrder(OrderStatus.FULFILLED, 2L);
            when(orderRepository.findById(fulfilled.getId())).thenReturn(Optional.of(fulfilled));

            assertThatThrownBy(() -> service.processPayment(fulfilled.getId(),
                    new ProcessPaymentCommand("CARD", "t", new BigDecimal("10.00"), "EUR", null)))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    @Nested @DisplayName("cancelOrder")
    class CancelOrder {

        @Test @DisplayName("PENDING → CANCELLED with reason stored and outbox entry saved")
        void success() {
            CustomerOrder pending = buildOrder(OrderStatus.PENDING, 0L);
            when(orderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(orderRepository.update(any())).thenAnswer(i -> i.getArgument(0));

            CustomerOrder cancelled = service.cancelOrder(pending.getId(), "changed mind");

            assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(cancelled.getCancelReason()).isEqualTo("changed mind");
            assertThat(cancelled.getVersion()).isEqualTo(1L);
            verify(outboxEntryFactory).forCancelled(any(), any(), eq("changed mind"));
            verify(outboxEventRepository).save(any());
        }

        @Test @DisplayName("FULFILLED order cannot be cancelled")
        void invalid_transition() {
            CustomerOrder fulfilled = buildOrder(OrderStatus.FULFILLED, 2L);
            when(orderRepository.findById(fulfilled.getId())).thenReturn(Optional.of(fulfilled));

            assertThatThrownBy(() -> service.cancelOrder(fulfilled.getId(), "late"))
                    .isInstanceOf(InvalidTransitionException.class);

            verify(outboxEventRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("fulfillOrder")
    class FulfillOrder {

        @Test @DisplayName("PAID → FULFILLED, outbox entry saved")
        void success() {
            CustomerOrder paid = buildOrder(OrderStatus.PAID, 1L);
            when(orderRepository.findById(paid.getId())).thenReturn(Optional.of(paid));
            when(orderRepository.update(any())).thenAnswer(i -> i.getArgument(0));

            CustomerOrder fulfilled = service.fulfillOrder(paid.getId());

            assertThat(fulfilled.getStatus()).isEqualTo(OrderStatus.FULFILLED);
            assertThat(fulfilled.getVersion()).isEqualTo(2L);
            verify(outboxEntryFactory).forFulfilled(any(), any());
            verify(outboxEventRepository).save(any());
        }

        @Test @DisplayName("PENDING order cannot be fulfilled directly")
        void invalid() {
            CustomerOrder pending = buildOrder(OrderStatus.PENDING, 0L);
            when(orderRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> service.fulfillOrder(pending.getId()))
                    .isInstanceOf(InvalidTransitionException.class);
        }
    }

    @Nested @DisplayName("CustomerOrder state machine")
    class StateMachine {

        @Test @DisplayName("withStatus returns a new instance — original is not mutated")
        void immutability() {
            CustomerOrder original  = buildOrder(OrderStatus.PAID, 1L);
            CustomerOrder fulfilled = original.withStatus(OrderStatus.FULFILLED, 2L);

            assertThat(original.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(fulfilled.getStatus()).isEqualTo(OrderStatus.FULFILLED);
            assertThat(fulfilled.getVersion()).isEqualTo(2L);
        }

        @Test @DisplayName("canTransitionTo — all legal transitions")
        void legal_transitions() {
            assertThat(buildOrder(OrderStatus.PENDING, 0).canTransitionTo(OrderStatus.PAID)).isTrue();
            assertThat(buildOrder(OrderStatus.PENDING, 0).canTransitionTo(OrderStatus.CANCELLED)).isTrue();
            assertThat(buildOrder(OrderStatus.PAID,    1).canTransitionTo(OrderStatus.FULFILLED)).isTrue();
            assertThat(buildOrder(OrderStatus.PAID,    1).canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test @DisplayName("canTransitionTo — terminal states reject all transitions")
        void terminal_states() {
            assertThat(buildOrder(OrderStatus.FULFILLED, 2).canTransitionTo(OrderStatus.CANCELLED)).isFalse();
            assertThat(buildOrder(OrderStatus.CANCELLED, 1).canTransitionTo(OrderStatus.PAID)).isFalse();
            assertThat(buildOrder(OrderStatus.PENDING,   0).canTransitionTo(OrderStatus.FULFILLED)).isFalse();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CustomerOrder buildOrder(OrderStatus status, long version) {
        return new CustomerOrder(
                UUID.randomUUID(), "idem-" + UUID.randomUUID(),
                List.of(new OrderItem(PID, "Widget", 1, new BigDecimal("10.00"))),
                status, null, version, Instant.now(), Instant.now(), null
        );
    }
}
