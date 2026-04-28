package com.omni.orders.infrastructure.persistence.outbox;

import com.omni.orders.domain.port.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA implementation of {@link OutboxEventRepository}.
 *
 * <p>{@link #save} uses {@code Propagation.MANDATORY}: it asserts an existing
 * transaction is active before writing. This is a safety net — if called
 * outside a transaction it throws immediately rather than silently creating
 * an orphaned outbox entry that could never be correlated with an order change.
 */
@Slf4j
@Repository
public class JpaOutboxRepository implements OutboxEventRepository {

    private final SpringDataOutboxRepository springDataOutboxRepository;

    public JpaOutboxRepository(SpringDataOutboxRepository springDataOutboxRepository) {
        this.springDataOutboxRepository = springDataOutboxRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(OutboxEntry entry) {
        springDataOutboxRepository.save(entry);
        log.debug("Outbox entry saved eventId={} orderId={} eventType={}",
                entry.getEventId(), entry.getOrderId(), entry.getEventType());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEntry> findPendingEntries() {
        return springDataOutboxRepository.findPendingOrderedByCreatedAt();
    }

    @Override
    @Transactional
    public boolean markProcessing(OutboxEntry entry) {
        boolean claimed = springDataOutboxRepository.claimForProcessing(entry.getId()) == 1;
        if (claimed) {
            entry.setStatus(OutboxStatus.PROCESSING);
        }
        return claimed;
    }

    @Override
    @Transactional
    public void markPublished(OutboxEntry entry) {
        entry.setStatus(OutboxStatus.PUBLISHED);
        springDataOutboxRepository.save(entry);
        log.debug("Outbox entry published eventId={}", entry.getEventId());
    }

    @Override
    @Transactional
    public void markFailed(OutboxEntry entry, String errorMessage) {
        entry.setStatus(OutboxStatus.FAILED);
        entry.setRetryCount(entry.getRetryCount() + 1);
        entry.setLastError(errorMessage != null && errorMessage.length() > 1000
                ? errorMessage.substring(0, 1000) : errorMessage);
        springDataOutboxRepository.save(entry);
        log.warn("Outbox entry failed eventId={} retries={} error={}",
                entry.getEventId(), entry.getRetryCount(), errorMessage);
    }
}
