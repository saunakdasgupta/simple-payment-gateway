# Payment Gateway

A Spring Boot REST API that allows merchants to process card payments and retrieve past payments via an acquiring bank.

## Requirements

- JDK 17
- Docker and Docker Compose

## Running the Application

The simplest way to run everything locally:

```bash
./run_local.sh
```

This starts the bank simulator in the background, waits until it is ready, then starts the gateway. Ctrl+C stops both.

Alternatively, start them separately:

```bash
docker-compose up -d bank_simulator
./gradlew bootRun
```

Or start everything via Docker Compose (requires building the image first):

```bash
docker-compose up
```

The gateway listens on `http://localhost:8090`.  
Index page: `http://localhost:8090/`  
Swagger UI: `http://localhost:8090/swagger-ui/index.html`  
Health check: `http://localhost:8090/actuator/health`  
Actuator: `http://localhost:8090/actuator`  
Metrics (Prometheus): `http://localhost:8090/actuator/prometheus`

## Running the Tests

```bash
./gradlew test
```

## API

### Process a Payment

```
POST /payments
```

Request body:

```json
{
  "card_number": "2222405343248877",
  "expiry_month": 4,
  "expiry_year": 2026,
  "currency": "GBP",
  "amount": 100,
  "cvv": "123"
}
```

Response (200 OK — Authorized or Declined):

```json
{
  "id": "8f433a5a-4317-457f-8bb6-876eeb6c9968",
  "status": "Authorized",
  "cardNumberLastFour": "8877",
  "expiryMonth": 4,
  "expiryYear": 2026,
  "currency": "GBP",
  "amount": 100
}
```

Response (400 Bad Request — Rejected due to validation failure):

```json
{
  "id": "3c1a5b2d-...",
  "status": "Rejected",
  "expiryMonth": 4,
  "expiryYear": 2026,
  "currency": "GBP",
  "amount": 100,
  "message": "card_number must be 14-19 numeric characters"
}
```

### Retrieve a Payment

```
GET /payments/{id}
```

Returns the stored payment response by UUID (200 OK), or 404 if not found.

## Design Decisions and Assumptions

### HTTP Status Codes

| Outcome | Status | Reason |
|---------|--------|--------|
| Authorized or Declined | 200 | The request was valid and processed |
| Rejected (validation failure) | 400 | The request was malformed; merchant should fix input |
| Payment ID not found | 404 | Standard REST convention |
| Bank unavailable (5xx, timeout, circuit OPEN) | 502 | Gateway acts as proxy; upstream failure maps to Bad Gateway |
| Gateway sent a malformed bank request | 500 | Gateway bug; a 4xx from the bank means our request was invalid |

### Rejected Payments Are Stored

Rejected payments are assigned a UUID and persisted with `status: "Rejected"`. This allows merchants to retrieve the outcome by ID for auditing or debugging. `cardNumberLastFour` is omitted from the response when the card number itself is invalid (null, non-numeric, or fewer than 4 characters).

### Card Number Handling

The full card number is accepted in the request and validated (14–19 numeric characters). Only the last four digits are stored in the response and logged. The full card number is never persisted or written to logs.

### CVV Handling

CVV is accepted as a string to correctly handle values with leading zeros (e.g., `"099"`). CVV is not stored or returned after processing — only used in the bank request.

### Transaction Direction

`POST /payments` exclusively represents a **debit (capture)** — the customer is paying the merchant and money is being taken from the customer's account. There is no credit/refund operation in this implementation.

In a full payment gateway, a refund would be a separate endpoint (e.g. `POST /payments/{id}/refunds`) with its own request model and flow. That is out of scope for this challenge.

### Amount Representation

`amount` is represented as a **positive integer in minor currency units** throughout the entire request/response/bank flow — pence for GBP, cents for USD, and so on. £10.50 is sent as `1050`.

`BigDecimal` is not used because there are no decimal places to represent once the amount is already in minor units. The floating-point precision problem that makes `BigDecimal` necessary for monetary values (e.g. `0.1` cannot be represented exactly in binary) does not apply to integers.

`int` is used rather than `long` for simplicity within the scope of this challenge. `int` supports values up to ~2.1 billion minor units (~£21 million), which is sufficient for the expected use case. In a production system, `long` would be the safer choice to accommodate large transactions without overflow risk.

### Validation

All validation is fail-fast: the first failing rule causes an immediate `Rejected` response; the bank is never called. Rules:

- `card_number`: 14–19 numeric characters
- `expiry_month`: 1–12
- `expiry_year` + `expiry_month`: must not be in the past
- `currency`: exactly 3 alphabetic characters (e.g., `GBP`, `USD`)
- `amount`: positive integer, in minor currency units (e.g. `1050` for £10.50)
- `cvv`: 3–4 numeric characters

### Exception Handling

`CommonExceptionHandler` extends `ResponseEntityExceptionHandler` so that Spring's built-in exception handling takes priority over the catch-all `@ExceptionHandler(Exception.class)`.

When a request body is missing or unparseable, Spring MVC throws `HttpMessageNotReadableException`. `ResponseEntityExceptionHandler.handleHttpMessageNotReadable()` intercepts it first and returns 400. Without the `extends` clause, the catch-all would intercept it instead and return 500.

The same applies to all Spring MVC framework exceptions (`MethodNotAllowedException`, `MissingServletRequestParameterException`, etc.) — the base class handles them before the catch-all can reach them.

A 4xx response from the bank (e.g., HTTP 400) is treated as a gateway bug, not a bank availability issue: it means the gateway constructed a malformed request. This surfaces as `RuntimeException` (→ 500), not `BankUnavailableException` (→ 502), and is logged at ERROR rather than WARN.

### Bank Communication

The gateway calls the acquiring bank at `bank.simulator.url` (configured in `application.properties`). The expiry date is formatted as `MM/YYYY` (zero-padded month) for the bank request. Any 5xx response from the bank is surfaced as `502 Bad Gateway` to the merchant.

### In-Memory Storage

Payments are stored in a `ConcurrentHashMap` keyed by UUID. This is intentionally simple for the scope of this challenge. In production, this would be replaced with a persistent store (e.g., PostgreSQL for durability) and a distributed cache (e.g., Redis) for horizontal scaling.

With an external database, `GET /payments/{id}` would introduce an additional failure surface beyond "not found" — connection failures, timeouts, and transient errors. These must be handled as a distinct error category and must not be conflated with the payment's own `message` field (which carries the rejection reason set at processing time and should never be modified after). A `PaymentRepositoryException` mapped to `503 Service Unavailable` via `CommonExceptionHandler` would be the appropriate pattern, keeping business outcomes and infrastructure failures in separate response types.

### RestTemplate

`RestTemplate` is used for synchronous HTTP calls to the bank. A 10-second connect and read timeout is configured. `WebClient` (reactive) was not used as it adds unnecessary complexity for a synchronous request-response flow.

## Observability

### Health Endpoints

A custom `BankSimulatorHealthIndicator` probes the acquiring bank on every health check. The app uses a custom `DEGRADED` status (HTTP 200) when the bank is unreachable — the gateway is still alive and can serve `GET /payments/{id}` requests, so it should not be removed from the load balancer.

| Endpoint | Bank down → | K8s action |
|---|---|---|
| `/actuator/health` | `DEGRADED` (HTTP 200) | Monitoring alert |
| `/actuator/health/liveness` | `UP` | No restart |
| `/actuator/health/readiness` | `UP` | Keep routing traffic |

A Resilience4J circuit breaker named `bankSimulator` is shared between the health probe and payment processing. After three consecutive connection failures the circuit opens. While open:

- `GET /actuator/health/bankSimulator` fast-fails immediately (no network attempt) and returns `DEGRADED`
- `POST /payments` fast-fails immediately with `CallNotPermittedException` → `BankUnavailableException` → 502

The circuit transitions to HALF_OPEN after `waitDurationInOpenState` (30s in production, 2s in tests), allows a single probe through, and returns to CLOSED on success.

### Metrics

The following endpoints are exposed:

| Endpoint | Contents |
|---|---|
| `/actuator/prometheus` | All metrics in Prometheus format (scrape target for Grafana) |
| `/actuator/metrics` | Individual metric lookup |
| `/actuator/info` | App name and description |

**Custom metrics:**

- `payments.processed{status="Authorized"}` — count of payments authorized by the bank
- `payments.processed{status="Declined"}` — count of payments declined by the bank
- `payments.processed{status="Rejected"}` — count of payments rejected by validation

Spring Boot auto-instruments `http.server.requests` (request count, latency, HTTP status) for all endpoints at no extra cost.

### Log Correlation

Requests carry two correlation IDs in MDC:

- `requestId` — a UUID assigned by `RequestCorrelationFilter` at the HTTP boundary, before any Spring processing. Covers log lines emitted before the payment ID is known (e.g., Jackson parse failures, Spring validation errors).
- `paymentId` — the UUID of the payment being processed, set by the service as soon as one is assigned.

Log format: `HH:mm:ss.SSS LEVEL [<requestId>][<paymentId>] logger - message`

```
10:14:05.001 INFO  [a1b2c3d4-...][3c1a5b2d-...] PaymentGatewayService - Processing payment 3c1a5b2d...
10:14:05.042 WARN  [a1b2c3d4-...][3c1a5b2d-...] MountebankBankClient  - Bank returned 503 from http://localhost:8080
10:14:05.043 WARN  [a1b2c3d4-...][3c1a5b2d-...] CommonExceptionHandler - Bank unavailable: Bank is currently unavailable
```

Non-payment logs (startup, health checks) leave both slots empty: `[][]`.

## Production Considerations

The following are out of scope for this challenge but would be required in a production system:

### Circuit Breaker: `ignoreExceptions` for Gateway Bugs

The circuit breaker currently records all exceptions as failures, including `HttpClientErrorException` (a 4xx from the bank, which means the gateway sent a malformed request). A gateway bug should not trip the circuit against a healthy bank.

In production, configure:
```properties
resilience4j.circuitbreaker.instances.bankSimulator.ignoreExceptions=\
  org.springframework.web.client.HttpClientErrorException
```

This causes the circuit breaker to re-throw the exception without recording it as a failure, so the 4xx propagates to the existing `HttpClientErrorException` catch block as normal.

### Idempotency

`POST /payments` is not idempotent. A merchant whose HTTP client times out and retries will submit a second payment request that the bank treats as a new transaction, potentially charging the customer twice.

The standard solution is a client-supplied `Idempotency-Key` header. The gateway stores the key-to-response mapping (e.g., in Redis with a 24-hour TTL) and returns the cached response on replay without calling the bank. Concurrent requests with the same key require a distributed lock to prevent both reaching the bank before the first response is stored.

### Thread Safety

`InMemoryPaymentRepository` uses a `ConcurrentHashMap` to handle concurrent writes safely. In production this is replaced by a persistent store, but the in-memory implementation is thread-safe for the scope of this challenge.

### Persistent Storage

Payments are stored in a `ConcurrentHashMap` that is lost on restart. In production this would be replaced with a persistent store (e.g., PostgreSQL) and a distributed cache (e.g., Redis) for horizontal scaling across multiple instances.

## Project Structure

```
src/main/java/com/checkout/payment/gateway/
├── client/
│   ├── BankClient.java                    interface — decouples service from HTTP transport
│   └── MountebankBankClient.java          RestTemplate implementation
├── configuration/
│   └── ApplicationConfiguration.java      RestTemplate bean with timeouts
├── controller/
│   └── PaymentGatewayController.java
├── enums/
│   └── PaymentStatus.java
├── exception/
│   ├── BankUnavailableException.java
│   ├── CommonExceptionHandler.java        @ControllerAdvice
│   └── PaymentNotFoundException.java
├── filter/
│   └── RequestCorrelationFilter.java      assigns requestId to MDC at the HTTP boundary
├── health/
│   └── BankSimulatorHealthIndicator.java  custom health indicator with circuit breaker
├── model/
│   ├── BankPaymentRequest.java            sent to acquiring bank
│   ├── BankPaymentResponse.java           received from acquiring bank
│   ├── ErrorResponse.java
│   ├── PaymentResponse.java               merchant response (GET and POST)
│   └── PostPaymentRequest.java            merchant POST request
├── repository/
│   ├── InMemoryPaymentRepository.java     ConcurrentHashMap implementation
│   └── PaymentRepository.java             interface — decouples service from storage
├── service/
│   └── PaymentGatewayService.java
├── validation/
│   └── PaymentRequestValidator.java
└── PaymentGatewayApplication.java
```
