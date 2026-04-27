# Order Service

Core service of the eCommerce Order Management System. Manages orders and their lifecycle.

## Prerequisites

- Java 17
- Maven 3.9+
- Docker Desktop (for Postgres + integration tests)

## Running Locally

### 1. Start Postgres

```bash
docker compose -f docker-compose-dev.yml up -d
```

### 2. Run the application

```bash
mvn spring-boot:run
```

The app starts at http://localhost:8080.

### 3. Stop Postgres

```bash
docker compose -f docker-compose-dev.yml down
```

## Running with Docker Compose (full stack)

```bash
docker compose up --build
```

Starts Postgres + app together with healthchecks.

## Endpoints

| URL | Description |
|---|---|
| http://localhost:8080/api/v1/ping | Liveness ping (placeholder) |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8080/actuator/prometheus | Prometheus metrics |
| http://localhost:8080/swagger-ui.html | OpenAPI / Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI JSON |

## Tests

```bash
mvn test
```

- **`PingControllerTest`** — `@WebMvcTest` slice, web layer only.
- **`PostgresContainerSmokeTest`** — `@DataJpaTest` + Testcontainers Postgres 15. Verifies Liquibase runs and JPA can connect.

`@SpringBootTest` is intentionally avoided. We use slice tests (`@WebMvcTest`, `@DataJpaTest`) for fast, focused feedback.

## Configuration

| Env var | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/order_db` | JDBC URL |
| `DB_USERNAME` | `order-user` | DB username |
| `DB_PASSWORD` | `order-pass` | DB password |

## Tech Stack

- Java 17, Spring Boot 3.2
- Spring Data JPA, PostgreSQL 15, Liquibase
- Spring Boot Actuator + Micrometer Prometheus
- SpringDoc OpenAPI (Swagger UI)
- Testcontainers (integration testing)
- Lombok

## Layout

```
src/
├── main/
│   ├── java/com/publicnext/orders/
│   │   ├── OrderServiceApplication.java
│   │   └── controller/PingController.java     # placeholder, will be removed
│   └── resources/
│       ├── application.yml
│       └── db/changelog-master.yml            # Liquibase, no changesets yet
└── test/
    └── java/com/publicnext/orders/
        ├── controller/PingControllerTest.java
        └── persistence/PostgresContainerSmokeTest.java
```
