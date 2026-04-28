package com.omni.orders.infrastructure.persistence.repository;

import com.omni.orders.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {
    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items LEFT JOIN FETCH o.payment WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithItemsAndPayment(@Param("id") UUID id);
    boolean existsByIdempotencyKey(String idempotencyKey);
}
