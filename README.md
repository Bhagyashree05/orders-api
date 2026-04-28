# Omni Selling — Backend Orders API

A production-leaning order management service built as part of the Omni Selling backend code challenge.

**Stack:** Java 21 · Spring Boot 3.2 · PostgreSQL · Hibernate/JPA · Apache Kafka · Flyway · Micrometer + Zipkin

---

## 1. Overview

This service implements the core order lifecycle for an e-commerce backend:

- Customers place orders from a product catalog
- Orders are paid, fulfilled, or cancelled via explicit state transitions
- Every state change is written atomically to a **transactional outbox** table and published to Kafka for downstream consumers (ERP, Warehouse)
- All state transitions are enforced by a domain-level state machine
- Idempotency keys prevent duplicate orders under retries
- Optimistic locking prevents concurrent state corruption

The project is intentionally scoped but production-leaning: structured logging with trace IDs, Zipkin tracing, Prometheus metrics, Flyway migrations, a proper hexagonal architecture, and a test pyramid from unit through E2E.

---

## 2. Architecture

The project follows **Hexagonal Architecture (Ports & Adapters)**. The domain layer has zero imports from Spring, Hibernate, or Kafka. All framework coupling lives in the infrastructure adapters.

```
┌─────────────────────────────────────────────────────────┐
│  API Layer                                              │
│  OrderController · PaymentController                    │
│  FulfillmentController · ProductController              │
│  @Valid Bean Validation · OrderApiMapper                │
└───────────────────────┬─────────────────────────────────┘
                        │ command objects
┌───────────────────────▼─────────────────────────────────┐
│  Domain Layer  (pure Java — zero framework imports)     │
│                                                         │
│  OrderApplicationService (interface)                    │
│  OrderApplicationServiceImpl                            │
│  · state machine guard (canTransitionTo)                │
│  · payment amount validation                            │
│  · price snapshot at order creation                     │
│  · writes Order + OutboxEntry in ONE @Transactional     │
│  · does NOT call Kafka directly                         │
└──────┬────────────────┬────────────────┬────────────────┘
       │ port           │ port           │ port
┌──────▼──────┐  ┌──────▼──────┐  ┌─────▼──────────────┐
│ JpaOrder    │  │ JpaProduct  │  │ JpaOutbox          │
│ Repository  │  │ Repository  │  │ Repository         │
│ (Hibernate) │  │ (Hibernate) │  │ Propagation        │
│             │  │             │  │ .MANDATORY         │
└─────────────┘  └─────────────┘  └────────┬───────────┘
                                            │ [separate thread]
                                  ┌─────────▼───────────┐
                                  │ OutboxRelayScheduler│
                                  │ CAS PENDING→PROCESS │
                                  │ sync Kafka send     │
                                  └─────────┬───────────┘
                                            │
                                  ┌─────────▼───────────┐
                                  │  Kafka order.events │
                                  │  ErpOrderConsumer   │
                                  │  WarehouseOrder     │
                                  │  Consumer           │
                                  └─────────────────────┘
```

### Package layout

```
com.omni.orders/
├── api/
│   ├── controller/      OrderController, PaymentController, FulfillmentController,
│   │                    ProductController, GlobalExceptionHandler
│   ├── dto/
│   │   ├── request/     CreateOrderRequest, OrderItemRequest, ProcessPaymentRequest,
│   │   │                CancelOrderRequest, PaymentMode
│   │   └── response/    OrderResponse, OrderItemResponse, PaymentResponse,
│   │                    ProductResponse, ErrorResponse
│   └── mapper/          OrderApiMapper  (DTOs ↔ domain + service commands)
│
├── domain/              Zero framework imports
│   ├── model/           Order (aggregate), OrderItem, OrderStatus, Payment, Product
│   ├── service/         OrderApplicationService (interface)
│   │                    OrderApplicationServiceImpl
│   │                    PlaceOrderCommand, OrderItemCommand, ProcessPaymentCommand
│   ├── port/            OrderRepository, ProductRepository,
│   │                    OutboxEventRepository, OrderEventPublisher
│   └── exception/       6 domain exceptions → HTTP via GlobalExceptionHandler
│
├── infrastructure/
│   ├── persistence/
│   │   ├── entity/      BaseEntity, OrderEntity, OrderItemEntity,
│   │   │                PaymentEntity, ProductEntity
│   │   ├── repository/  SpringDataOrderRepository, SpringDataPaymentRepository,
│   │   │                SpringDataProductRepository
│   │   ├── adapter/     JpaOrderRepository, JpaProductRepository
│   │   └── outbox/      OutboxEntry, OutboxStatus, OrderEventType,
│   │                    OutboxEntryFactory, SpringDataOutboxRepository,
│   │                    JpaOutboxRepository, OutboxRelayScheduler
│   ├── kafka/
│   │   ├── producer/    OrderEventMessage<T>, OrderEventMessageBuilder,
│   │   │                OrderCreatedPayload, OrderStatusChangedPayload,
│   │   │                OrderCancelledPayload, OrderItemPayload,
│   │   │                KafkaOrderEventPublisher
│   │   ├── consumer/    ErpOrderConsumer, WarehouseOrderConsumer
│   │   └── config/      MessagingTopics, KafkaTopicConfig
│   └── observability/   TraceContextFilter  (requestId → MDC)
│
└── config/              JpaConfig (@EnableJpaAuditing + OpenAPI bean)
```

---

## 3. Key Design Decisions

| Decision | Rationale |
|---|---|
| **Hexagonal architecture** | Domain is fully decoupled from Hibernate, Kafka, and Spring. Unit tests run with Mockito — no DB needed. |
| **Immutable domain model** | `Order` is a plain class; every transition returns a new instance. No accidental mutation, no thread-safety concerns. |
| **Transactional Outbox** | Order update and outbox INSERT are atomic. Kafka publish is decoupled via a scheduler. No event loss on broker downtime. |
| **Separate `PaymentEntity` table** | Payment has its own lifecycle and query patterns. FK lives on the payments side so inserting a payment never touches the orders row. |
| **`OrderItemEntity` as full `@Entity`** | Own PK enables independent item queries (returns, disputes, analytics). Cleaner Hibernate SQL than `@ElementCollection`. |
| **`@Version` optimistic locking** | Hibernate appends `WHERE version = ?` to every UPDATE. Concurrent requests get 409 instead of silent data corruption. |
| **`UNIQUE(idempotency_key)`** | DB-enforced deduplication. Re-sending the same key returns 409 instead of creating a duplicate order. |
| **Constructor injection everywhere** | No `@Autowired` on fields. Dependencies explicit, testable, final. |
| **Separate controllers per bounded context** | OrderController, PaymentController, FulfillmentController — each has its own auth scope, request model, and error surface. In production these would be independent microservices. |
| **Generic `OrderEventMessage<T>`** | One Kafka topic, one envelope type, typed payloads per event. Consumers filter on `eventType`; schema evolves independently per payload. |

---

## 4. How to Run

### Prerequisites

- Docker Desktop (must be running before any `docker-compose` command)
- Java 21 JDK
- Maven 3.9+

---

### How to Build

```bash
# Compile and package (skip tests)
mvn clean package -DskipTests

# Compile and package (with tests)
mvn clean package
```

---

### How to Run Tests

> **Do not run `docker-compose up` before running tests.**
> Integration and E2E tests use **Testcontainers**, which automatically starts its own
> isolated Postgres and Kafka containers inside the test JVM and tears them down when
> the tests finish. They are completely independent of `docker-compose.yml`.
>
> **Docker Desktop must be open and running** for `mvn verify` to execute the integration
> and E2E tests. If Docker is not available, all `*IT` tests are **skipped** (not failed)
> and `mvn verify` still reports **BUILD SUCCESS** — the unit tests always run regardless.

```bash
# Unit tests only — no Docker needed at all (pure Mockito, no DB, no Kafka)
mvn test

# All tests — unit + integration + E2E
# Docker Desktop must be running; Testcontainers handles everything else automatically
mvn verify

# Run a specific test class
mvn test -Dtest=OrderApplicationServiceTest
mvn verify -Dit.test=OrderControllerIT
mvn verify -Dit.test=OrderPersistenceIT
mvn verify -Dit.test=KafkaOutboxRelayIT
mvn verify -Dit.test=OrderLifecycleE2EIT
```

| Command | Docker Desktop needed? | `docker-compose up` needed? | Without Docker |
|---|---|---|---|
| `mvn test` | No | No | 12 unit tests pass ✅ |
| `mvn verify` | Yes (Testcontainers) | No | IT tests skipped, BUILD SUCCESS ✅ |
| `mvn spring-boot:run` | Yes | **Yes** | Fails to connect to DB ❌ |

> `*Test.java` — unit tests, run by `mvn test`, no infrastructure required.
> `*IT.java` — integration and E2E tests, run by `mvn verify`, Testcontainers manages containers automatically.

---

### How to Run Locally

#### Option 1: Maven

```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8080`.
Swagger UI available at `http://localhost:8080/swagger-ui.html`.

> Requires Postgres and Kafka to be running. Start them first with:
> ```bash
> docker-compose up postgres kafka zipkin -d
> ```

---

#### Option 2: Docker Compose (recommended — starts everything)

```bash
# Build and start Postgres, Kafka, Zipkin, and the application
docker-compose up --build

# Stop
docker-compose down

# Stop and wipe the Postgres volume (clean slate)
docker-compose down -v
```

Service starts on `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`
Zipkin traces: `http://localhost:9411`

---

#### Option 3: Docker only

```bash
# Build the image
docker build -t omni-orders-api .

# Run (requires Postgres and Kafka already running and reachable)
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/ordersdb \
  -e DB_USER=orders \
  -e DB_PASSWORD=orders \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  omni-orders-api
```

---

#### Option 4: IntelliJ IDEA

**Step 1 — Set Project SDK to Java 21**

`File` → `Project Structure` → `Project` → `SDK` → select or add **Java 21**
Set `Language level` to **21**.

**Step 2 — Set Maven JVM to Java 21**

`Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven` → `Runner`
→ `JRE` → select **Java 21**.

**Step 3 — Reload Maven**

Right-click `pom.xml` → `Maven` → `Reload project`.

**Step 4 — Start backing services**

```bash
docker-compose up postgres kafka zipkin -d
```

**Step 5 — Run the application**

Open `OrdersApplication.java` → click the green **▶ Run** button.

Add `-Dspring.profiles.active=local` in `Run/Debug Configurations` → `VM options`
for human-readable console logs.

**Step 6 — Run tests**

Right-click any test class → `Run`, or right-click `src/test` → `Run 'All Tests'`.
Testcontainers starts Docker containers automatically.

> Verify setup: open Terminal inside IDEA and run `java -version` — must show `openjdk 21`.


## 5. API Reference

### Base URL
```
http://localhost:8080/api/v1
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/products` | List all products in the catalog |
| POST | `/orders` | Create a new order (idempotent via `idempotencyKey`) |
| GET | `/orders/{id}` | Get order by ID |
| POST | `/orders/{id}/payments` | Process payment — PENDING → PAID |
| POST | `/orders/{id}/fulfillments` | Mark as fulfilled — PAID → FULFILLED |
| POST | `/orders/{id}/cancel` | Cancel order — PENDING or PAID → CANCELLED |

### Error response shape

All errors return the same JSON envelope:

```json
{
  "error":     "INVALID_STATE_TRANSITION",
  "message":   "Cannot transition order from FULFILLED to PAID",
  "details":   null,
  "timestamp": "2024-05-01T12:34:56.789Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

`details` is an array of field-level messages, present only for `400 VALIDATION_ERROR`.

---

### List products

```bash
curl http://localhost:8080/api/v1/products
```

**Response (200):**
```json
[
  { "id": "3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6", "sku": "SKU-001", "name": "Wireless Headphones", "price": 49.99 },
  { "id": "b1c2d3e4-f5a6-3b7c-8d9e-0a1b2c3d4e5f", "sku": "SKU-002", "name": "USB-C Hub",            "price": 29.99 },
  { "id": "c2d3e4f5-a6b7-3c8d-9e0f-1a2b3c4d5e6f", "sku": "SKU-003", "name": "Mechanical Keyboard",  "price": 89.99 },
  { "id": "d3e4f5a6-b7c8-3d9e-0f1a-2b3c4d5e6f7a", "sku": "SKU-004", "name": "Standing Desk Mat",    "price": 39.99 },
  { "id": "e4f5a6b7-c8d9-3e0f-1a2b-3c4d5e6f7a8b", "sku": "SKU-005", "name": "Webcam HD 1080p",      "price": 59.99 }
]
```

---

### Create an order

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "checkout-abc-001",
    "items": [
      { "productId": "3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6", "quantity": 2 }
    ]
  }'
```

**Response (201 Created):**
```json
{
  "id":             "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "idempotencyKey": "checkout-abc-001",
  "status":         "PENDING",
  "totalAmount":    99.98,
  "items": [
    {
      "productId":   "3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6",
      "productName": "Wireless Headphones",
      "quantity":    2,
      "unitPrice":   49.99,
      "itemTotal":   99.98
    }
  ],
  "payment":      null,
  "version":      0,
  "createdAt":    "2024-05-01T12:00:00Z",
  "updatedAt":    "2024-05-01T12:00:00Z",
  "cancelReason": null
}
```

**Multi-item order:**
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "idempotencyKey": "checkout-abc-002",
    "items": [
      { "productId": "3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6", "quantity": 1 },
      { "productId": "b1c2d3e4-f5a6-3b7c-8d9e-0a1b2c3d4e5f", "quantity": 2 }
    ]
  }'
```

**Error — duplicate idempotency key (409):**
```json
{
  "error":     "DUPLICATE_ORDER",
  "message":   "Order already exists for idempotency key: checkout-abc-001",
  "timestamp": "2024-05-01T12:00:01Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

---

### Get an order

> Replace `{ORDER_ID}` with the `id` from the create response in all examples below.

```bash
curl http://localhost:8080/api/v1/orders/{ORDER_ID}
```

**Response (200):**
```json
{
  "id":             "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "idempotencyKey": "checkout-abc-001",
  "status":         "PENDING",
  "totalAmount":    99.98,
  "items": [
    {
      "productId":   "3a9f2d1e-f3b5-3d29-96bf-c8b2c3e4d5f6",
      "productName": "Wireless Headphones",
      "quantity":    2,
      "unitPrice":   49.99,
      "itemTotal":   99.98
    }
  ],
  "payment":      null,
  "version":      0,
  "createdAt":    "2024-05-01T12:00:00Z",
  "updatedAt":    "2024-05-01T12:00:00Z",
  "cancelReason": null
}
```

**Error — not found (404):**
```json
{
  "error":     "NOT_FOUND",
  "message":   "Order not found: f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "timestamp": "2024-05-01T12:00:00Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

---

### Process payment

> `amount` must exactly match `totalAmount`. Partial payments are not supported.
> Supported modes: `CARD`, `UPI`, `CASH`, `BANK_TRANSFER`.
> `paidAt` is optional — defaults to server time if omitted.

```bash
curl -X POST http://localhost:8080/api/v1/orders/{ORDER_ID}/payments \
  -H "Content-Type: application/json" \
  -d '{
    "paymentMode":   "CARD",
    "transactionId": "txn_stripe_abc123",
    "amount":        99.98,
    "currency":      "EUR"
  }'
```

**Response (200):**
```json
{
  "id":          "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "status":      "PAID",
  "totalAmount": 99.98,
  "payment": {
    "paymentMode":   "CARD",
    "transactionId": "txn_stripe_abc123",
    "amount":        99.98,
    "currency":      "EUR",
    "paidAt":        "2024-05-01T12:05:00Z"
  },
  "version":   1,
  "updatedAt": "2024-05-01T12:05:00Z"
}
```

**Error — amount mismatch (422):**
```json
{
  "error":     "PAYMENT_AMOUNT_MISMATCH",
  "message":   "Payment amount 50.00 does not match order total 99.98",
  "timestamp": "2024-05-01T12:05:00Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

**Error — invalid transition, e.g. paying an already-PAID order (422):**
```json
{
  "error":     "INVALID_STATE_TRANSITION",
  "message":   "Cannot transition order from PAID to PAID",
  "timestamp": "2024-05-01T12:05:00Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

---

### Fulfill an order

> Order must be in `PAID` status. Typically called by the warehouse system after shipment.

```bash
curl -X POST http://localhost:8080/api/v1/orders/{ORDER_ID}/fulfillments
```

**Response (200):**
```json
{
  "id":      "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "status":  "FULFILLED",
  "version": 2,
  "payment": {
    "paymentMode":   "CARD",
    "transactionId": "txn_stripe_abc123",
    "amount":        99.98,
    "currency":      "EUR",
    "paidAt":        "2024-05-01T12:05:00Z"
  },
  "updatedAt": "2024-05-01T12:10:00Z"
}
```

**Error — fulfilling a PENDING order (422):**
```json
{
  "error":     "INVALID_STATE_TRANSITION",
  "message":   "Cannot transition order from PENDING to FULFILLED",
  "timestamp": "2024-05-01T12:10:00Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

---

### Cancel an order

> Can be cancelled from `PENDING` or `PAID`. A reason is required.

```bash
curl -X POST http://localhost:8080/api/v1/orders/{ORDER_ID}/cancel \
  -H "Content-Type: application/json" \
  -d '{ "reason": "Customer changed their mind" }'
```

**Response (200):**
```json
{
  "id":           "f1e2d3c4-b5a6-7890-abcd-ef1234567890",
  "status":       "CANCELLED",
  "cancelReason": "Customer changed their mind",
  "version":      1,
  "updatedAt":    "2024-05-01T12:03:00Z"
}
```

**Error — cancelling a FULFILLED order (422):**
```json
{
  "error":     "INVALID_STATE_TRANSITION",
  "message":   "Cannot transition order from FULFILLED to CANCELLED",
  "timestamp": "2024-05-01T12:03:00Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

**Error — blank reason (400):**
```json
{
  "error":   "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": ["reason: reason must not be blank"],
  "timestamp": "2024-05-01T12:03:00Z",
  "traceId":   "6b7f4a2c8e1d5f3a"
}
```

> The Swagger UI at `http://localhost:8080/swagger-ui.html` can also execute all endpoints directly from the browser.

---

## 6. Domain / State Machine

```
PENDING ──► PAID ──► FULFILLED
   │           │
   └───────────┴──► CANCELLED
```

**Transition rules:**

| From | Allowed transitions |
|---|---|
| PENDING | PAID, CANCELLED |
| PAID | FULFILLED, CANCELLED |
| FULFILLED | *(terminal — no further transitions)* |
| CANCELLED | *(terminal — no further transitions)* |

Invalid transitions return `422 Unprocessable Entity` with error code `INVALID_STATE_TRANSITION`.

**Implementation:** The `Order` class contains a `canTransitionTo(OrderStatus next)` method — a pure guard with no side effects. `OrderApplicationServiceImpl.checkTransitionAllowed()` calls it before any update. This keeps the state machine in the domain, not scattered across controllers.

**Immutability:** `Order` is a plain class with no setters. Every transition method (`withPayment`, `withCancellation`, `withStatus`) returns a new `Order` instance. The original is never mutated, making concurrent reads safe and the state machine trivially testable without mocking.

---

## 7. Event Design (Kafka)

### Topic

All events are published to a single topic: **`order.events`**

Message key = `orderId` — guarantees all events for the same order land on the same partition, preserving causal ordering for all consumer groups.

### Envelope: `OrderEventMessage<T>`

```json
{
  "eventId":       "uuid — consumer dedup key for at-least-once delivery",
  "eventType":     "ORDER_PAID",
  "schemaVersion": 1,
  "occurredAt":    "2024-05-01T12:34:56Z",
  "orderId":       "uuid",
  "traceId":       "6b7f4a2c — Zipkin trace for log correlation",
  "requestId":     "uuid — from X-Request-ID header",
  "payload":       { ... }
}
```

`traceId` and `requestId` are injected from MDC at publish time, so every Kafka message carries the observability context of the originating HTTP request.

### Typed payloads

| `eventType` | Payload class | Fields |
|---|---|---|
| `ORDER_CREATED` | `OrderCreatedPayload` | `status`, `totalAmount`, `currency`, `items[]` |
| `ORDER_PAID` | `OrderStatusChangedPayload` | `previousStatus`, `newStatus` |
| `ORDER_FULFILLED` | `OrderStatusChangedPayload` | `previousStatus`, `newStatus` |
| `ORDER_CANCELLED` | `OrderCancelledPayload` | `previousStatus`, `newStatus`, `reason` |

`ORDER_CREATED` includes item details so ERP consumers can bootstrap without a second API call. `ORDER_PAID` and `ORDER_FULFILLED` are intentionally lean — consumers that need full order data call `GET /api/v1/orders/{orderId}`.

### Consumers

| Consumer | Group ID | Reacts to |
|---|---|---|
| `ErpOrderConsumer` | `erp-consumer` | All 4 event types — creates/confirms/voids sales orders |
| `WarehouseOrderConsumer` | `warehouse-consumer` | PAID (start pick), FULFILLED (confirm shipment), CANCELLED (release stock) |

Independent consumer groups: ERP lag never blocks the warehouse consumer.

---

## 8. Persistence / Outbox

### Database schema (Flyway)

| Table | Purpose |
|---|---|
| `products` | Seeded by V2 migration — read-only at runtime |
| `orders` | `@Version` optimistic locking · UNIQUE `idempotency_key` |
| `order_items` | Full `@Entity` (own PK) · renamed from `order_lines` |
| `payments` | Separate table · FK on payments side · UNIQUE `transaction_id` |
| `order_outbox` | Transactional outbox — guarantees at-least-once Kafka delivery |

Hibernate is set to `ddl-auto: validate` — it never auto-creates or modifies the schema. All schema changes go through Flyway.

### Optimistic locking

Hibernate's `@Version` appends `WHERE version = :expected` to every UPDATE and auto-increments the column. A concurrent winner causes `OptimisticLockException`, translated to `409 CONCURRENT_MODIFICATION`. Clients retry with a fresh GET.

### Idempotency

`UNIQUE(idempotency_key)` on the orders table. Duplicate inserts raise `DataIntegrityViolationException`, translated to `409 DUPLICATE_ORDER`.

### Transactional Outbox Pattern

**Why not call Kafka directly inside `@Transactional`:**

```java
// WRONG — common mistake
@Transactional
public Order processPayment(...) {
    orderRepository.update(paid);          // DB write
    kafkaTemplate.send("order.events"...); // Kafka send — outside DB tx boundary
    // Broker down → exception → DB rolls back → order change is lost
    // DB commits but send() fails → event silently dropped
}
```

**The solution:**

1. Order state change + `OutboxEntry` INSERT are in the **same DB transaction** (atomic)
2. `OutboxRelayScheduler` polls `order_outbox` every N ms (configurable via `outbox.poll-interval-ms`)
3. CAS `UPDATE SET status='PROCESSING' WHERE status='PENDING'` prevents double-publishing across instances
4. Synchronous `kafkaTemplate.send().get()` — failure propagates back so the entry is marked FAILED for retry
5. Success marks the entry PUBLISHED

**Status lifecycle:** `PENDING → PROCESSING → PUBLISHED` or `PENDING → PROCESSING → FAILED` (with `retry_count` and `last_error`)

**Production upgrade path → Debezium CDC:**
- Deploy Debezium Postgres connector pointed at this DB
- Debezium tails the Postgres WAL as a replication client
- Every INSERT to `order_outbox` triggers an immediate change event forwarded to Kafka
- Zero polling overhead, sub-second latency, no missed entries during application downtime

---

## 9. Observability

| Signal | Details |
|---|---|
| **Structured JSON logs** | Every line contains `traceId`, `spanId`, `requestId`. JSON format in docker/prod; human-readable in `local` profile. |
| **Distributed tracing** | Micrometer Brave → Zipkin at `http://localhost:9411`. Spans cover HTTP, Hibernate queries, Kafka produce. |
| **Metrics** | Prometheus at `GET /actuator/prometheus`. JVM, Hikari pool, HTTP request counts. |
| **Health** | `GET /actuator/health` — reports Postgres connectivity and application status. |

`requestId` is sourced from the `X-Request-ID` request header (gateway passthrough) or generated as a UUID if absent. It is echoed in the `X-Request-ID` response header and included in every Kafka event envelope, enabling end-to-end correlation: HTTP request → outbox relay → Kafka consumer logs.

---

## 10. Testing Strategy

### Test pyramid

| Layer | Class | Type | Infrastructure |
|---|---|---|---|
| Unit | `OrderApplicationServiceTest` | Pure Mockito | None — no DB, no Kafka |
| Persistence | `OrderPersistenceIT` | Integration | Testcontainers Postgres |
| Kafka | `KafkaOutboxRelayIT` | Integration | Testcontainers Postgres + Kafka |
| API | `OrderControllerIT` | Integration | Testcontainers Postgres + Kafka |
| E2E | `OrderLifecycleE2EIT` | End-to-end | Testcontainers Postgres + Kafka |

### What each layer validates

**`OrderApplicationServiceTest`** — business rules in isolation: state machine guard, payment amount validation, price snapshotting, outbox factory calls, immutability (transition returns new instance without mutating original).

**`OrderPersistenceIT`** — JPA adapter directly: save+findById round-trip with items, `PaymentEntity` in separate table, `DataIntegrityViolationException` → `DuplicateOrderException`, Hibernate `@Version` CAS failure → `OptimisticLockException`, Spring Data auditing (`createdAt`/`updatedAt` auto-populated), outbox CAS (only one instance can claim an entry).

**`KafkaOutboxRelayIT`** — full outbox relay: HTTP request → DB write → scheduler polls → Kafka publish. Asserts `eventType`, `orderId`, typed payload shape, and `traceId`/`requestId` fields in the envelope for all 4 event types.

**`OrderControllerIT`** — all HTTP endpoints and status codes: 201 on create, 409 duplicate key, 422 unknown product, 400 validation with field details, 404 not found, 422 amount mismatch, payment block present after pay, version increments, terminal state rejects further transitions.

**`OrderLifecycleE2EIT`** — 6 complete scenarios: happy path (PENDING→PAID→FULFILLED, version 0→1→2), cancel after payment (payment data preserved), idempotent create (same key returns 409, original unchanged), payment mismatch (order stays PENDING at version 0), error responses always contain traceId, multi-SKU order totals correctly.

### Testcontainers singleton

`IntegrationTestBase` declares `PostgreSQLContainer` and `KafkaContainer` as `static` fields with `withReuse(true)`. All test classes share the same containers — started once per JVM, not once per class. This cuts integration suite startup from ~30s to ~5s.

---

## 11. Bug Fix — JPQL Enum Literal in `SpringDataOutboxRepository`

### Root Cause

`SpringDataOutboxRepository` contained two JPQL queries that compared an `@Enumerated(EnumType.STRING)` field against **string literals** (`'PENDING'`, `'PROCESSING'`):

```java
// ❌ BEFORE — string literal in JPQL enum comparison (type mismatch at runtime)
@Query("SELECT e FROM OutboxEntry e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
List<OutboxEntry> findPendingOrderedByCreatedAt();

@Modifying
@Query("""
        UPDATE OutboxEntry e
           SET e.status = 'PROCESSING'
         WHERE e.id = :id AND e.status = 'PENDING'
        """)
int claimForProcessing(@Param("id") UUID id);
```

In JPQL, string literals (`'PENDING'`) are always of type `String`. When the field being compared is a Java `enum` — even one stored as a `STRING` in the database — the JPA specification requires the comparison value to be an **enum constant**, not a string literal. Hibernate raises a `TypeMismatchException` at query execution time, causing:

- `findPendingOrderedByCreatedAt()` to throw at every outbox poll — outbox relay never processes any entries.
- `claimForProcessing()` to throw — the CAS claim step always fails, blocking event publishing entirely.

**Affected tests:** all integration and E2E tests that exercise the Kafka outbox relay path (`KafkaOutboxRelayIT`, `OrderLifecycleE2EIT`, `OrderControllerIT` full lifecycle, `OrderPersistenceIT` outbox tests).

### Fix

Replace string literals with fully-qualified JPQL enum constant references. Also add `clearAutomatically = true` to `@Modifying` on the bulk `UPDATE` so Hibernate clears the first-level cache after the update, ensuring subsequent reads see the new status rather than a stale pre-update snapshot:

```java
// ✅ AFTER — correct JPQL enum constant reference
@Query("SELECT e FROM OutboxEntry e WHERE e.status = com.omni.orders.infrastructure.persistence.outbox.OutboxStatus.PENDING ORDER BY e.createdAt ASC")
List<OutboxEntry> findPendingOrderedByCreatedAt();

@Modifying(clearAutomatically = true)
@Query("""
        UPDATE OutboxEntry e
           SET e.status = com.omni.orders.infrastructure.persistence.outbox.OutboxStatus.PROCESSING
         WHERE e.id = :id AND e.status = com.omni.orders.infrastructure.persistence.outbox.OutboxStatus.PENDING
        """)
int claimForProcessing(@Param("id") UUID id);
```

**File changed:** `src/main/java/com/omni/orders/infrastructure/persistence/outbox/SpringDataOutboxRepository.java`

---

## 12. Trade-offs & Out-of-scope

### Bounded context note

In a real production system, **Orders, Payments, Products, and Fulfilment** would be independent microservices with their own databases, teams, and deployment cycles. This codebase uses **separate controllers** for each concern to demonstrate bounded-context isolation, but they run in one deployable for task purposes.

### Acknowledged trade-offs

| Item | Decision | Production remedy |
|---|---|---|
| Outbox relay is a scheduler | Simple, no extra infra to deploy | Replace with Debezium CDC |
| At-least-once delivery | Event may be re-published on app restart during relay | Consumers dedup on `eventId` |
| Products in same service | Simpler for task scope | Dedicated product microservice |
| No stock reservation | Out of scope | Saga pattern with inventory service |
| No auth/authorisation | Out of scope | OAuth2 / JWT via Spring Security |
| Payment amount = total only | No partial payments | PSP integration (Stripe/Adyen) |
| Single-instance Caffeine cache removed | No cache; direct DB for products | Redis for multi-instance deployments |

### Out of scope

Authentication/authorisation · Stock reservation · Order listing/pagination ·
PSP integration (Stripe, Adyen) · Event sourcing · Admin/backoffice APIs · Multi-tenancy
