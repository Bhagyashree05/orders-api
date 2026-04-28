package com.omni.orders.infrastructure.persistence.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEntry, UUID> {

    @Query("SELECT e FROM OutboxEntry e WHERE e.status = com.omni.orders.infrastructure.persistence.outbox.OutboxStatus.PENDING ORDER BY e.createdAt ASC")
    List<OutboxEntry> findPendingOrderedByCreatedAt();

    /**
     * Atomic CAS: sets PROCESSING only if current status is PENDING.
     * Returns 1 on success, 0 if another instance already claimed this entry.
     *
     * <p>{@code clearAutomatically = true} clears the first-level cache after the
     * bulk UPDATE so subsequent findById calls see the new status rather than the
     * pre-update snapshot held by the persistence context.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE OutboxEntry e
               SET e.status = com.omni.orders.infrastructure.persistence.outbox.OutboxStatus.PROCESSING
             WHERE e.id = :id AND e.status = com.omni.orders.infrastructure.persistence.outbox.OutboxStatus.PENDING
            """)
    int claimForProcessing(@Param("id") UUID id);
}
