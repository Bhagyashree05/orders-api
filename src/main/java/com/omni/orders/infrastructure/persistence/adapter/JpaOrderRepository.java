package com.omni.orders.infrastructure.persistence.adapter;

import com.omni.orders.domain.exception.DuplicateOrderException;
import com.omni.orders.domain.exception.OptimisticLockException;
import com.omni.orders.domain.model.CustomerOrder;
import com.omni.orders.domain.model.OrderItem;
import com.omni.orders.domain.model.Payment;
import com.omni.orders.domain.port.OrderRepository;
import com.omni.orders.infrastructure.persistence.entity.OrderEntity;
import com.omni.orders.infrastructure.persistence.entity.OrderItemEntity;
import com.omni.orders.infrastructure.persistence.entity.PaymentEntity;
import com.omni.orders.infrastructure.persistence.repository.SpringDataOrderRepository;
import com.omni.orders.infrastructure.persistence.repository.SpringDataPaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of the {@link OrderRepository} domain port.
 *
 * <p>This class is the only place that knows about both domain model types
 * ({@link CustomerOrder}) and Hibernate entity types ({@link OrderEntity}).
 */
@Slf4j
@Repository
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderRepository   springDataOrderRepository;
    private final SpringDataPaymentRepository springDataPaymentRepository;

    public JpaOrderRepository(SpringDataOrderRepository springDataOrderRepository,
                               SpringDataPaymentRepository springDataPaymentRepository) {
        this.springDataOrderRepository   = springDataOrderRepository;
        this.springDataPaymentRepository = springDataPaymentRepository;
    }

    @Override
    @Transactional
    public CustomerOrder save(CustomerOrder order) {
        try {
            OrderEntity entity = toNewEntity(order);
            OrderEntity saved  = springDataOrderRepository.saveAndFlush(entity);
            log.debug("CustomerOrder persisted orderId={} version={}", saved.getId(), saved.getVersion());
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate idempotency key={}", order.getIdempotencyKey());
            throw new DuplicateOrderException(order.getIdempotencyKey());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findById(UUID id) {
        return springDataOrderRepository.findByIdWithItemsAndPayment(id)
                .map(this::toDomain);
    }

    @Override
    @Transactional
    public CustomerOrder update(CustomerOrder order) {
        try {
            OrderEntity entity = springDataOrderRepository
                    .findByIdWithItemsAndPayment(order.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "CustomerOrder disappeared: " + order.getId()));

            entity.setStatus(order.getStatus());
            entity.setCancelReason(order.getCancelReason());

            if (order.getPayment() != null && entity.getPayment() == null) {
                PaymentEntity pe = toPaymentEntity(order.getPayment(), entity);
                springDataPaymentRepository.save(pe);
                entity.setPayment(pe);
            }

            OrderEntity updated = springDataOrderRepository.saveAndFlush(entity);
            log.debug("CustomerOrder updated orderId={} status={} version={}",
                    updated.getId(), updated.getStatus(), updated.getVersion());
            return toDomain(updated);

        } catch (jakarta.persistence.OptimisticLockException |
                 org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
            log.warn("Optimistic lock conflict orderId={}", order.getId());
            throw new OptimisticLockException(order.getId().toString());
        }
    }

    // ── Domain → Entity ───────────────────────────────────────────────────────

    private OrderEntity toNewEntity(CustomerOrder order) {
        OrderEntity entity = new OrderEntity(
                order.getId(), order.getIdempotencyKey(), order.getStatus());
        order.getItems().forEach(item -> {
            OrderItemEntity ie = OrderItemEntity.builder()
                    .id(UUID.randomUUID())
                    .productId(item.getProductId())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .build();
            entity.addItem(ie);
        });
        return entity;
    }

    private PaymentEntity toPaymentEntity(Payment payment, OrderEntity orderEntity) {
        return new PaymentEntity(UUID.randomUUID(), orderEntity, payment.getMode(),
                payment.getTransactionId(), payment.getAmount(),
                payment.getCurrency(), payment.getPaidAt());
    }

    // ── Entity → Domain ───────────────────────────────────────────────────────

    private CustomerOrder toDomain(OrderEntity e) {
        List<OrderItem> items = e.getItems().stream()
                .map(i -> new OrderItem(i.getProductId(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice()))
                .toList();

        Payment payment = null;
        if (e.getPayment() != null) {
            PaymentEntity pe = e.getPayment();
            payment = new Payment(pe.getMode(), pe.getTransactionId(),
                    pe.getAmount(), pe.getCurrency(), pe.getPaidAt());
        }

        return new CustomerOrder(e.getId(), e.getIdempotencyKey(), items, e.getStatus(),
                payment, e.getVersion(), e.getCreatedAt(), e.getUpdatedAt(), e.getCancelReason());
    }
}
