package com.omni.orders.infrastructure.persistence.outbox;

import com.omni.orders.domain.port.OrderEventPublisher;
import com.omni.orders.domain.port.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls {@code order_outbox} for PENDING entries and publishes them to Kafka.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Fetch all PENDING entries ordered by {@code created_at}.</li>
 *   <li>For each entry, perform an atomic CAS UPDATE:
 *       {@code WHERE status = 'PENDING' → PROCESSING}. If 0 rows updated,
 *       another scheduler instance claimed it first — skip.</li>
 *   <li>Publish via {@link OrderEventPublisher} (synchronous send to Kafka).</li>
 *   <li>On success: mark PUBLISHED.</li>
 *   <li>On failure: increment {@code retryCount}; if exhausted → mark FAILED.</li>
 * </ol>
 *
 * <h3>At-least-once guarantee</h3>
 * If the application restarts between publish and markPublished, the entry
 * remains PROCESSING and is re-picked on startup (PROCESSING entries older
 * than N minutes can be reset to PENDING by an additional cleanup job — out
 * of scope here but noted as a production concern).
 *
 * <h3>Production upgrade path → Debezium CDC</h3>
 * <ol>
 *   <li>Deploy Debezium Postgres connector pointing at this DB.</li>
 *   <li>Configure it to monitor the {@code order_outbox} table.</li>
 *   <li>Every INSERT to {@code order_outbox} appears as a WAL change event.</li>
 *   <li>Debezium forwards it to a Kafka topic (e.g. {@code db.order_outbox}).</li>
 *   <li>A Kafka Streams job or a separate consumer reads that topic and calls
 *       the Orders API (or publishes directly to {@code order.events}).</li>
 *   <li>Benefits: zero polling overhead, sub-second latency, no missed entries
 *       during application downtime.</li>
 * </ol>
 */
@Slf4j
@Component
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderEventPublisher   orderEventPublisher;
    private final int                   maxRetries;

    public OutboxRelayScheduler(OutboxEventRepository outboxEventRepository,
                                OrderEventPublisher orderEventPublisher,
                                @Value("${outbox.max-retries:3}") int maxRetries) {
        this.outboxEventRepository = outboxEventRepository;
        this.orderEventPublisher   = orderEventPublisher;
        this.maxRetries            = maxRetries;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    public void relay() {
        List<OutboxEntry> pending = outboxEventRepository.findPendingEntries();
        if (pending.isEmpty()) return;

        log.debug("Outbox relay: processing {} pending entries", pending.size());

        for (OutboxEntry entry : pending) {
            processEntry(entry);
        }
    }

    private void processEntry(OutboxEntry entry) {
        if (entry.getRetryCount() >= maxRetries) {
            log.error("Outbox entry exceeded max retries eventId={} orderId={}",
                    entry.getEventId(), entry.getOrderId());
            outboxEventRepository.markFailed(entry,
                    "Max retries (" + maxRetries + ") exceeded");
            return;
        }

        boolean claimed = outboxEventRepository.markProcessing(entry);
        if (!claimed) {
            log.debug("Outbox entry already claimed, skipping. eventId={}", entry.getEventId());
            return;
        }

        try {
            orderEventPublisher.publish(entry);
            outboxEventRepository.markPublished(entry);
        } catch (Exception ex) {
            log.error("Failed to publish outbox entry eventId={} orderId={}: {}",
                    entry.getEventId(), entry.getOrderId(), ex.getMessage(), ex);
            outboxEventRepository.markFailed(entry, ex.getMessage());
        }
    }
}
