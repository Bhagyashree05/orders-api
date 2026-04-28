package com.omni.orders.domain.service;

import com.omni.orders.domain.model.CustomerOrder;

import java.util.UUID;

/**
 * Application service interface — defines the order lifecycle use-cases.
 *
 * <p>Controllers depend on this interface, not the implementation.
 * This enables clean constructor injection and easy test substitution.
 *
 * <p>The implementation ({@link OrderApplicationServiceImpl}) holds all
 * orchestration logic: domain rule enforcement, repository calls, and
 * outbox entry creation.
 */
public interface OrderApplicationService {

    CustomerOrder placeOrder(PlaceOrderCommand command);

    CustomerOrder getOrder(UUID orderId);

    CustomerOrder processPayment(UUID orderId, ProcessPaymentCommand command);

    CustomerOrder cancelOrder(UUID orderId, String reason);

    CustomerOrder fulfillOrder(UUID orderId);
}
