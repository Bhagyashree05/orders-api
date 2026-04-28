package com.omni.orders.domain.port;

import com.omni.orders.domain.model.CustomerOrder;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port — domain-defined contract for order persistence.
 * Infrastructure implements this; domain depends only on this interface.
 */
public interface OrderRepository {
    /** @throws com.omni.orders.domain.exception.DuplicateOrderException on duplicate idempotencyKey */
    CustomerOrder save(CustomerOrder order);
    Optional<CustomerOrder> findById(UUID id);
    /** @throws com.omni.orders.domain.exception.OptimisticLockException on concurrent modification */
    CustomerOrder update(CustomerOrder order);
}
