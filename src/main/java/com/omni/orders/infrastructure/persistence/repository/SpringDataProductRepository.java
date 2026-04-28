package com.omni.orders.infrastructure.persistence.repository;

import com.omni.orders.infrastructure.persistence.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataProductRepository extends JpaRepository<ProductEntity, UUID> {
    Optional<ProductEntity> findByIdAndActiveTrue(UUID id);
    List<ProductEntity>     findAllByActiveTrue();
}
