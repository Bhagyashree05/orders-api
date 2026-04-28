package com.omni.orders.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Read-only product catalog entry.
 *
 * <p>In a real system this would come from a dedicated Product microservice.
 * For this task it is a Postgres table seeded by Flyway V2, accessed via
 * the {@link com.omni.orders.domain.port.ProductRepository} port.
 */
public class Product {

    private final UUID       id;
    private final String     sku;
    private final String     name;
    private final BigDecimal price;

    public Product(UUID id, String sku, String name, BigDecimal price) {
        this.id    = id;
        this.sku   = sku;
        this.name  = name;
        this.price = price;
    }

    public UUID       getId()    { return id; }
    public String     getSku()   { return sku; }
    public String     getName()  { return name; }
    public BigDecimal getPrice() { return price; }
}
