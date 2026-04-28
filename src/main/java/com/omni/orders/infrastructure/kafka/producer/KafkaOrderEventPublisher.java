package com.omni.orders.infrastructure.kafka.producer;

import com.omni.orders.domain.port.OrderEventPublisher;
import com.omni.orders.infrastructure.kafka.config.MessagingTopics;
import com.omni.orders.infrastructure.persistence.outbox.OutboxEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Kafka implementation of {@link OrderEventPublisher}.
 *
 * <p>Called exclusively by {@link com.omni.orders.infrastructure.persistence.outbox.OutboxRelayScheduler}.
 * The application service never calls this directly — the outbox pattern
 * enforces the separation.
 *
 * <h3>Synchronous send</h3>
 * Uses {@code .get()} to block until Kafka confirms the write. This allows the
 * relay scheduler to catch failures and mark entries as FAILED for retry, rather
 * than silently dropping events with fire-and-forget.
 *
 * <h3>Message key = orderId</h3>
 * All events for the same order land on the same Kafka partition, preserving
 * causal ordering for all consumer groups.
 */
@Slf4j
@Component
public class KafkaOrderEventPublisher implements OrderEventPublisher {

    private final KafkaTemplate<String, OrderEventMessage<?>> kafkaTemplate;
    private final OrderEventMessageBuilder                     messageBuilder;

    public KafkaOrderEventPublisher(
            KafkaTemplate<String, OrderEventMessage<?>> kafkaTemplate,
            OrderEventMessageBuilder messageBuilder) {
        this.kafkaTemplate  = kafkaTemplate;
        this.messageBuilder = messageBuilder;
    }

    @Override
    public void publish(OutboxEntry entry) {
        OrderEventMessage<?> message = messageBuilder.build(entry);
        String key = entry.getOrderId().toString();

        try {
            SendResult<String, OrderEventMessage<?>> result =
                    kafkaTemplate.send(MessagingTopics.ORDER_EVENTS, key, message).get();

            log.info("[Kafka] Published eventId={} orderId={} eventType={} partition={} offset={}",
                    message.getEventId(), message.getOrderId(), message.getEventType(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "Interrupted while publishing event " + entry.getEventId(), ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException(
                    "Failed to publish event " + entry.getEventId(), ex.getCause());
        }
    }
}
