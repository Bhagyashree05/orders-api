package com.omni.orders.infrastructure.kafka.consumer;

import com.omni.orders.infrastructure.kafka.config.MessagingTopics;
import com.omni.orders.infrastructure.kafka.producer.OrderCancelledPayload;
import com.omni.orders.infrastructure.kafka.producer.OrderEventMessage;
import com.omni.orders.infrastructure.kafka.producer.OrderStatusChangedPayload;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Simulated Warehouse / WMS consumer. Renamed from WarehouseConsumer.
 */
@Slf4j
@Component
public class WarehouseOrderConsumer {

    @KafkaListener(
            topics = MessagingTopics.ORDER_EVENTS,
            groupId = "warehouse-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, OrderEventMessage<?>> record) {
        OrderEventMessage<?> event = record.value();

        switch (event.getEventType()) {
            case ORDER_PAID -> {
                OrderStatusChangedPayload p = (OrderStatusChangedPayload) event.getPayload();
                log.info("[WAREHOUSE] ORDER_PAID orderId={} — fetching items from API to start pick",
                        event.getOrderId());
            }
            case ORDER_FULFILLED -> {
                OrderStatusChangedPayload p = (OrderStatusChangedPayload) event.getPayload();
                log.info("[WAREHOUSE] Shipment confirmed orderId={}", event.getOrderId());
            }
            case ORDER_CANCELLED -> {
                OrderCancelledPayload p = (OrderCancelledPayload) event.getPayload();
                log.info("[WAREHOUSE] Releasing stock orderId={} reason={}", event.getOrderId(), p.getReason());
            }
            case ORDER_CREATED ->
                log.debug("[WAREHOUSE] No warehouse action for ORDER_CREATED orderId={}", event.getOrderId());
        }
    }
}
