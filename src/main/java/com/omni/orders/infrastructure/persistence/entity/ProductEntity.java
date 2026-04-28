package com.omni.orders.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Products table — seeded by Flyway V2, read-only at runtime. */
@Entity
@Table(name = "products",
        uniqueConstraints = @UniqueConstraint(name = "uq_products_sku", columnNames = "sku"))
@Getter @Setter @NoArgsConstructor
public class ProductEntity extends BaseEntity {

    @Column(name = "sku",    nullable = false, updatable = false, length = 50)  private String     sku;
    @Column(name = "name",   nullable = false, length = 255)                    private String     name;
    @Column(name = "price",  nullable = false, precision = 12, scale = 2)       private BigDecimal price;
    @Column(name = "active", nullable = false)                                  private boolean    active = true;

    public ProductEntity(UUID id, String sku, String name, BigDecimal price) {
        super(id); this.sku = sku; this.name = name; this.price = price;
    }
}
