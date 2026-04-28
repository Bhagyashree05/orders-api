package com.omni.orders.domain.port;

import com.omni.orders.infrastructure.persistence.outbox.OutboxEntry;

/**
 * Output port for publishing order events to the message broker.
 * Called exclusively by the outbox relay scheduler — never directly by the application service.
 */
public interface OrderEventPublisher {
    void publish(OutboxEntry entry);
}
