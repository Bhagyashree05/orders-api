package com.omni.orders.domain.service;

import com.omni.orders.domain.exception.InvalidTransitionException;
import com.omni.orders.domain.exception.OrderNotFoundException;
import com.omni.orders.domain.exception.PaymentAmountMismatchException;
import com.omni.orders.domain.exception.ProductNotFoundException;
import com.omni.orders.domain.model.CustomerOrder;
import com.omni.orders.domain.model.OrderItem;
import com.omni.orders.domain.model.OrderStatus;
import com.omni.orders.domain.model.Payment;
import com.omni.orders.domain.model.Product;
import com.omni.orders.domain.port.OrderRepository;
import com.omni.orders.domain.port.OutboxEventRepository;
import com.omni.orders.domain.port.ProductRepository;
import com.omni.orders.infrastructure.persistence.outbox.OutboxEntryFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full order lifecycle.
 *
 * <h3>Why this service does not publish Kafka events directly</h3>
 * <p>Publishing Kafka events inside a {@code @Transactional} method is a
 * common mistake. It looks correct but creates two failure modes:
 * <ul>
 *   <li>If Kafka is unavailable, the producer throws — causing the DB
 *       transaction to roll back. The order change is lost even though it
 *       was business-valid.</li>
 *   <li>If the DB commits but the send fails after commit, the event is
 *       silently dropped. Downstream systems never learn about the transition.</li>
 * </ul>
 *
 * <h3>Transactional Outbox Pattern (implemented here)</h3>
 * <ol>
 *   <li>CustomerOrder state change and outbox entry are written to Postgres in the
 *       <em>same transaction</em> — both commit or both roll back.</li>
 *   <li>{@link com.omni.orders.infrastructure.persistence.outbox.OutboxRelayScheduler}
 *       polls the outbox table separately and publishes to Kafka.</li>
 *   <li>Kafka success → entry marked PUBLISHED. Failure → entry marked FAILED
 *       and retried up to {@code outbox.max-retries} times.</li>
 * </ol>
 * This guarantees at-least-once delivery. Consumers use {@code eventId}
 * for idempotency (dedup on their side).
 *
 * <h3>Production upgrade path</h3>
 * Replace the scheduler with Debezium CDC: Debezium tails the Postgres WAL,
 * captures every INSERT to {@code order_outbox} as a change event, and
 * forwards it to Kafka without polling. Zero-latency, no missed entries
 * during application downtime.
 *
 * <h3>Transaction strategy</h3>
 * <ul>
 *   <li>Write methods: {@code @Transactional} — order update + outbox insert are atomic.</li>
 *   <li>{@code getOrder}: {@code @Transactional(readOnly=true)} — Hibernate read-only
 *       session, no dirty-checking, measurably faster on query-heavy paths.</li>
 * </ul>
 */
@Slf4j
@Service
public class OrderApplicationServiceImpl implements OrderApplicationService {

    private final OrderRepository      orderRepository;
    private final ProductRepository    productRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEntryFactory   outboxEntryFactory;

    public OrderApplicationServiceImpl(OrderRepository orderRepository,
                                       ProductRepository productRepository,
                                       OutboxEventRepository outboxEventRepository,
                                       OutboxEntryFactory outboxEntryFactory) {
        this.orderRepository      = orderRepository;
        this.productRepository    = productRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEntryFactory   = outboxEntryFactory;
    }

    @Override
    @Transactional
    public CustomerOrder placeOrder(PlaceOrderCommand command) {
        log.info("Placing order idempotencyKey={} itemCount={}",
                command.getIdempotencyKey(), command.getItems().size());

        List<OrderItem> items = command.getItems().stream()
                .map(this::resolveOrderItem)
                .toList();

        CustomerOrder order = new CustomerOrder(
                UUID.randomUUID(),
                command.getIdempotencyKey(),
                items,
                OrderStatus.PENDING,
                null,
                0L,
                Instant.now(),
                Instant.now(),
                null
        );

        CustomerOrder saved = orderRepository.save(order);
        outboxEventRepository.save(outboxEntryFactory.forCreated(saved));
        log.info("CustomerOrder placed orderId={} total={}", saved.getId(), saved.getTotalAmount());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerOrder getOrder(UUID orderId) {
        return findOrderById(orderId);
    }

    @Override
    @Transactional
    public CustomerOrder processPayment(UUID orderId, ProcessPaymentCommand command) {
        CustomerOrder order = findOrderById(orderId);
        checkTransitionAllowed(order, OrderStatus.PAID);
        validatePaymentAmount(order, command.getAmount());

        Payment payment = new Payment(
                command.getMode(),
                command.getTransactionId(),
                command.getAmount(),
                command.getCurrency(),
                command.getPaidAt() != null ? command.getPaidAt() : Instant.now()
        );

        CustomerOrder paid   = order.withPayment(payment, order.getVersion() + 1);
        CustomerOrder stored = orderRepository.update(paid);

        outboxEventRepository.save(outboxEntryFactory.forPaid(stored, order.getStatus()));
        log.info("CustomerOrder paid orderId={} mode={} txn={}",
                orderId, payment.getMode(), payment.getTransactionId());
        return stored;
    }

    @Override
    @Transactional
    public CustomerOrder cancelOrder(UUID orderId, String reason) {
        CustomerOrder order     = findOrderById(orderId);
        checkTransitionAllowed(order, OrderStatus.CANCELLED);

        CustomerOrder cancelled = order.withCancellation(reason, order.getVersion() + 1);
        CustomerOrder stored    = orderRepository.update(cancelled);

        outboxEventRepository.save(
                outboxEntryFactory.forCancelled(stored, order.getStatus(), reason));
        log.info("CustomerOrder cancelled orderId={} reason={}", orderId, reason);
        return stored;
    }

    @Override
    @Transactional
    public CustomerOrder fulfillOrder(UUID orderId) {
        CustomerOrder order     = findOrderById(orderId);
        checkTransitionAllowed(order, OrderStatus.FULFILLED);

        CustomerOrder fulfilled = order.withStatus(OrderStatus.FULFILLED, order.getVersion() + 1);
        CustomerOrder stored    = orderRepository.update(fulfilled);

        outboxEventRepository.save(outboxEntryFactory.forFulfilled(stored, order.getStatus()));
        log.info("CustomerOrder fulfilled orderId={}", orderId);
        return stored;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CustomerOrder findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId.toString()));
    }

    private void checkTransitionAllowed(CustomerOrder order, OrderStatus next) {
        if (!order.canTransitionTo(next)) {
            throw new InvalidTransitionException(order.getStatus(), next);
        }
    }

    private void validatePaymentAmount(CustomerOrder order, BigDecimal tendered) {
        if (order.getTotalAmount().compareTo(tendered) != 0) {
            throw new PaymentAmountMismatchException(order.getTotalAmount(), tendered);
        }
    }

    private OrderItem resolveOrderItem(OrderItemCommand cmd) {
        Product product = productRepository.findById(cmd.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(
                        cmd.getProductId().toString()));
        return new OrderItem(
                product.getId(),
                product.getName(),
                cmd.getQuantity(),
                product.getPrice()
        );
    }
}
