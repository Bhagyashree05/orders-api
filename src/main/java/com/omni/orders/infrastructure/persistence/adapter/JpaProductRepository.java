package com.omni.orders.infrastructure.persistence.adapter;

import com.omni.orders.domain.model.Product;
import com.omni.orders.domain.port.ProductRepository;
import com.omni.orders.infrastructure.persistence.repository.SpringDataProductRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA implementation of {@link ProductRepository}.
 *
 * <p>No caching — products are served directly from Postgres.
 * Products are seeded by Flyway V2 and read-only at runtime.
 *
 * <p><b>Architecture note (README):</b> In a real production system, Orders,
 * Payments, Products, and Fulfilment would each be independent microservices
 * with their own databases. Separate controllers in this codebase demonstrate
 * bounded-context isolation, but they run in one deployable for task purposes.
 */
@Repository
public class JpaProductRepository implements ProductRepository {

    private final SpringDataProductRepository springDataProductRepository;

    public JpaProductRepository(SpringDataProductRepository springDataProductRepository) {
        this.springDataProductRepository = springDataProductRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID id) {
        return springDataProductRepository.findByIdAndActiveTrue(id)
                .map(e -> new Product(e.getId(), e.getSku(), e.getName(), e.getPrice()));
    }

    @Override
    @Transactional(readOnly = true)
    public Collection<Product> findAll() {
        return springDataProductRepository.findAllByActiveTrue().stream()
                .map(e -> new Product(e.getId(), e.getSku(), e.getName(), e.getPrice()))
                .toList();
    }
}
