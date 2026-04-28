package com.omni.orders.domain.port;

import com.omni.orders.infrastructure.persistence.outbox.OutboxEntry;
import java.util.List;

/**
 * Output port for the transactional outbox.
 *
 * <p>{@link #save} must be called within the same transaction as the order update.
 * {@link #findPendingEntries} and the mark-* methods are called by the relay scheduler.
 */
public interface OutboxEventRepository {
    /** Must be called in the same transaction as the order state change. */
    void save(OutboxEntry entry);
    List<OutboxEntry> findPendingEntries();
    /** CAS: sets PROCESSING only if currently PENDING. Returns true if claimed. */
    boolean markProcessing(OutboxEntry entry);
    void markPublished(OutboxEntry entry);
    void markFailed(OutboxEntry entry, String errorMessage);
}
