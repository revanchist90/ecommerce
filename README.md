# eCommerce Order Management System

A microservices implementation of the PublicNext interview brief: an order
management system with event-driven notification and analytics pipelines.

## Services

| Service              | Port | Storage     | Role                                            |
|----------------------|------|-------------|-------------------------------------------------|
| order-service        | 8080 | Postgres 15 | Order CRUD, status transitions, transactional outbox |
| notification-service | 8081 | stateless   | Consumes order events, dispatches email/SMS with CB + DLQ retry topology |
| analytics-service    | 8082 | MongoDB 7   | Maintains materialized views over order events; exposes a small reporting API |

## Architecture

```
                                                    ┌─── Postgres (orders, outbox, audit)
                                                    │
   HTTP ──► order-service ────────────────────────┐ │
                  │  writes order + outbox row    │ │
                  │  in one transaction           ▼ │
                  │                            [outbox]
                  │                                │
                  │                       OutboxPoller
                  │                  (FOR UPDATE SKIP LOCKED)
                  │                                │
                  │                                ▼
                  │                    ┌──── Kafka: orders.events ────┐
                  │                    │                              │
                  ▼                    ▼                              ▼
          GET /orders, ...    notification-service             analytics-service
                                       │                              │
                                       ▼                              ▼
                              EmailGateway (CB)              MongoDB materialized views:
                                       │ open                  - order_metrics_daily
                                       ▼                       - status_counts
                              SmsGateway (fallback)            - customer_stats
                                       │                              │
                              orders.events-retry-0                   ▼
                                       │  (5s backoff)        GET /api/v1/analytics/...
                              orders.events-retry-1
                                       │  (30s backoff)
                              orders.events-dlt
```

## Running locally

Requires Docker.

```bash
docker compose up --build
```

This starts:
- Bitnami Kafka 3.6 in KRaft mode (no ZooKeeper) on `localhost:9092`
- Postgres 15 on `localhost:5432`
- MongoDB 7 on `localhost:27017`
- All three services on the ports above

Initial startup takes a minute or two while Maven builds each service image.

## Sample requests

```bash
# Create an order
curl -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "00000000-0000-0000-0000-000000000001",
    "orderLines": [
      {"productId": "11111111-1111-1111-1111-111111111111", "quantity": 2, "unitPrice": 10.00}
    ]
  }'

# Transition status (UNPROCESSED → PROCESSING → PROCESSED → SHIPPED)
curl -X PATCH http://localhost:8080/api/v1/orders/<orderId>/status \
  -H 'Content-Type: application/json' -d '{"status":"PROCESSING"}'

# Soft-delete
curl -X DELETE http://localhost:8080/api/v1/orders/<orderId>

# List with filters and pagination
curl "http://localhost:8080/api/v1/orders?status=UNPROCESSED&page=0&size=20"

# Analytics
curl "http://localhost:8082/api/v1/analytics/daily?from=2026-04-01&to=2026-04-30"
curl  http://localhost:8082/api/v1/analytics/status-distribution
curl "http://localhost:8082/api/v1/analytics/top-customers?limit=10"
```

A notification "send" is logged by the notification-service container — `docker compose logs notification-service` shows lines like `[email] to=customer:... subject="Order received" ...`.

## Tests

Each service has its own test suite. From the service directory:

```bash
mvn verify
```

Tests use Testcontainers (Postgres, Kafka, MongoDB), so Docker must be running.

| Service              | Test count |
|----------------------|------------|
| order-service        | 75         |
| notification-service | 14         |
| analytics-service    | 31         |

## Patterns demonstrated

- **Transactional outbox** (order-service). Domain change and event row commit in one Postgres transaction. A scheduled poller publishes to Kafka via `FOR UPDATE SKIP LOCKED`, so multiple replicas can poll concurrently without double-publishing.
- **CQRS read models** (analytics-service). Three materialized Mongo collections updated by Kafka consumer; REST endpoints query the views directly.
- **Idempotent consumer / claim pattern** (analytics-service). Consumer inserts a `processed_events` row keyed by the producer-side `outbox_id` header before applying the update — duplicate Kafka deliveries are skipped on `DuplicateKeyException`.
- **Circuit breaker with fallback** (notification-service). Resilience4j wraps the email gateway. When the circuit opens (configurable failure rate / window), `CallNotPermittedException` propagates and the dispatcher falls through to the SMS gateway.
- **Three-topic retry topology with exponential backoff and DLT** (notification-service). Spring Kafka `@RetryableTopic` produces `orders.events-retry-0` (5s), `orders.events-retry-1` (30s), `orders.events-dlt`. A `@DltHandler` logs permanent failures.
- **Per-aggregate ordering** (Kafka). `orderId` as the partition key guarantees per-order event order across consumers, even though the topic isn't globally ordered.
- **Optimistic locking, soft delete, audit history** (order-service). `@Version`, Hibernate `@SQLRestriction("deleted_at IS NULL")`, append-only `order_events` table.
- **Auto-progression scheduler** (order-service). Configurable per-status delays move stale orders along the lifecycle (UNPROCESSED → PROCESSING → PROCESSED → SHIPPED).

## Design notes

- **Single Kafka topic for one aggregate type.** `orders.events` carries every event type for orders, with `event_type` in a header for consumer-side filtering and `aggregate_id` (= orderId) as the partition key. This trades per-event-type subscription convenience for stronger per-aggregate ordering and fewer topics to operate.
- **No JPA in notification-service.** A pure consumer that logs "sent" doesn't need a database. Idempotency is accepted as best-effort (offset commits + the cost of an occasional duplicate log line). If the email mocks were a real channel the trade-off would change.
- **Stateful analytics, atomic updates.** `MongoTemplate.upsert` with `$inc` mutates counters atomically; `BigDecimal` revenue is mapped to `Decimal128` so `$inc` accepts it.
- **Event Processing & Integration service is intentionally not built.** The brief lists it but doesn't describe a concrete external system to integrate with (e.g. a payment gateway). A pass-through middleman with no business job would be ceremony. If a real integration is wanted, this is the right place to add it.

## Known gaps

- `OrderStatusChangedPayload` and `OrderDeletedPayload` don't carry `customerId` / `totalAmount`. As a result, analytics' `customer_stats` is incremented on `ORDER_CREATED` but not decremented on `ORDER_DELETED`. Fix: either widen those payloads in order-service or maintain a per-order tracking collection in analytics.
- The outbox poller's per-event sync send (`KafkaTemplate.send().get()`) serializes the batch. Fine for the demo; under load, send all in parallel and `.get()` each future.
- Trace propagation through the outbox is not implemented. Each layer (HTTP request, poller, consumer) gets its own trace. To cross the async boundary, persist a `traceparent` column on the outbox and inject it as a Kafka header in `KafkaOutboxPublisher`.
- Cross-service end-to-end is exercised per service via Testcontainers, not as a single composed integration test. Running `docker compose up` and hitting the endpoints is the manual end-to-end check.

## Layout

```
ecommerce/
├── docker-compose.yml          # full stack: kafka + postgres + mongo + 3 services
├── README.md                   # this file
├── order-service/
├── notification-service/
└── analytics-service/
```
