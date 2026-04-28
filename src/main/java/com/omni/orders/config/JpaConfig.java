package com.omni.orders.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JPA infrastructure configuration.
 *
 * <p>{@code @EnableJpaAuditing} activates Spring Data's {@code @CreatedDate} and
 * {@code @LastModifiedDate} support on all entities extending
 * {@link com.omni.orders.infrastructure.persistence.entity.BaseEntity}.
 *
 * <p>{@code @EnableJpaRepositories} scopes repository scanning to the two
 * infrastructure packages that contain Spring Data interfaces, preventing
 * accidental scanning of unrelated packages.
 *
 * <p>{@code @EnableTransactionManagement} activates annotation-driven transaction
 * management so {@code @Transactional} on service and adapter methods is honoured.
 */
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {
        "com.omni.orders.infrastructure.persistence.repository",
        "com.omni.orders.infrastructure.persistence.outbox"
})
@EnableTransactionManagement
public class JpaConfig {
}
