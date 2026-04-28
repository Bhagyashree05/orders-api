package com.omni.orders.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 *
 * <p>Produces the spec bean consumed by SpringDoc to generate the Swagger UI
 * at {@code /swagger-ui.html} and the raw spec at {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ordersApiSpec() {
        return new OpenAPI().info(new Info()
                .title("Omni Orders API")
                .description("""
                        Backend Orders API — Omni Selling Code Challenge.

                        ## Architecture note
                        In a real production system, Orders, Payments, Products, and Fulfilment
                        are independent microservices with their own databases. Separate controllers
                        demonstrate bounded-context isolation here, but they run in one deployable.

                        ## Outbox pattern
                        State changes are written to an `order_outbox` table in the same DB transaction.
                        A scheduler polls the outbox and publishes lean events to Kafka.
                        Production upgrade: replace scheduler with Debezium CDC on the Postgres WAL.

                        ## State machine
                        ```
                        PENDING → PAID → FULFILLED
                           │         │
                           └─────────┴→ CANCELLED
                        ```
                        """)
                .version("1.0.0"));
    }
}
