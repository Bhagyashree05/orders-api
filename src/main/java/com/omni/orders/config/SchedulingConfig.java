package com.omni.orders.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} annotation processing.
 *
 * <p>Kept in a separate {@code @Configuration} class (rather than on
 * {@code OrdersApplication}) so it can be disabled in tests by setting:
 * <pre>
 *   spring.scheduling.enabled=false
 * </pre>
 * This prevents the {@link com.omni.orders.infrastructure.persistence.outbox.OutboxRelayScheduler}
 * from polling the outbox in the background during integration tests, which would
 * exhaust HikariCP connections when Testcontainers recycles Postgres containers
 * between test classes.
 */
@Configuration
@ConditionalOnProperty(name = "spring.scheduling.enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
public class SchedulingConfig {
}
