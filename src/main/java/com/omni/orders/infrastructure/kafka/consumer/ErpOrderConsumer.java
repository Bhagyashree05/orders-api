package com.omni.orders.infrastructure.kafka.consumer;

import com.omni.orders.infrastructure.kafka.config.MessagingTopics;
import com.omni.orders.infrastructure.kafka.producer.OrderCancelledPayload;
import com.omni.orders.infrastructure.kafka.producer.OrderCreatedPayload;
import com.omni.orders.infrastructure.kafka.producer.OrderEventMessage;
import com.omni.orders.infrastructure.kafka.producer.OrderStatusChangedPayload;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Simulated ERP consumer. Branches on eventType; casts payload to the correct type.
 */
@Slf4j
@Component
public class ErpOrderConsumer {

    @KafkaListener(
            topics = MessagingTopics.ORDER_EVENTS,
            groupId = "erp-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEventMessage<?>> record) {
        OrderEventMessage<?> event = record.value();
        log.info("[ERP] Received eventType={} orderId={} traceId={}",
                event.getEventType(), event.getOrderId(), event.getTraceId());

        switch (event.getEventType()) {
            case ORDER_CREATED -> {
                OrderCreatedPayload p = (OrderCreatedPayload) event.getPayload();
                log.info("[ERP] Creating draft sales order orderId={} status={}",
                        event.getOrderId(), p.getStatus());
            }
            case ORDER_PAID -> {
                OrderStatusChangedPayload p = (OrderStatusChangedPayload) event.getPayload();
                log.info("[ERP] Confirming payment orderId={} {}→{} — fetch order for amount",
                        event.getOrderId(), p.getPreviousStatus(), p.getNewStatus());
            }
            case ORDER_CANCELLED -> {
                OrderCancelledPayload p = (OrderCancelledPayload) event.getPayload();
                log.info("[ERP] Voiding order orderId={} reason={}", event.getOrderId(), p.getReason());
            }
            case ORDER_FULFILLED ->
                log.debug("[ERP] No ERP action for ORDER_FULFILLED orderId={}", event.getOrderId());
        }
    }
}
