package com.omni.orders.domain.port;

import com.omni.orders.domain.model.Product;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Optional<Product> findById(UUID id);
    Collection<Product> findAll();
}
