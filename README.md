# Payment Gateway

A Spring Boot REST API that allows merchants to process card payments and retrieve past payments via an acquiring bank.

## Requirements

- JDK 17
- Docker and Docker Compose

## Running the Application

Start the bank simulator and the gateway together:

```bash
docker-compose up
```

Or run the bank simulator separately and start the gateway via Gradle:

```bash
docker-compose up bank_simulator
./gradlew bootRun
```

The gateway listens on `http://localhost:8090`.  
Swagger UI: `http://localhost:8090/swagger-ui/index.html`  
Health check: `http://localhost:8090/actuator/health`

## Running the Tests

```bash
./gradlew test
```

## API

### Process a Payment

```
POST /payment
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
  "cardNumberLastFour": 8877,
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
  "cardNumberLastFour": 0,
  "expiryMonth": 0,
  "expiryYear": 0,
  "currency": null,
  "amount": 0
}
```

### Retrieve a Payment

```
GET /payment/{id}
```

Returns the stored payment response by UUID (200 OK), or 404 if not found.

## Design Decisions and Assumptions

### HTTP Status Codes

| Outcome | Status | Reason |
|---------|--------|--------|
| Authorized or Declined | 200 | The request was valid and processed |
| Rejected (validation failure) | 400 | The request was malformed; merchant should fix input |
| Payment ID not found | 404 | Standard REST convention |
| Bank unavailable (503 from bank) | 502 | Gateway acts as proxy; upstream failure maps to Bad Gateway |

### Rejected Payments Are Stored

Rejected payments are assigned a UUID and persisted with `status: "Rejected"`. This allows merchants to retrieve the outcome by ID for auditing or debugging. `cardNumberLastFour` defaults to `0` when the card number itself is invalid (null, non-numeric, or fewer than 4 characters).

### Card Number Handling

The full card number is accepted in the request and validated (14–19 numeric characters). Only the last four digits are stored in the response and logged. The full card number is never persisted or written to logs.

### CVV Handling

CVV is accepted as a string to correctly handle values with leading zeros (e.g., `"099"`). CVV is not stored or returned after processing — only used in the bank request.

### Transaction Direction

`POST /payment` exclusively represents a **debit (capture)** — the customer is paying the merchant and money is being taken from the customer's account. There is no credit/refund operation in this implementation.

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

### Bank Communication

The gateway calls the acquiring bank at `bank.simulator.url` (configured in `application.properties`). The expiry date is formatted as `MM/YYYY` (zero-padded month) for the bank request. A `503` from the bank is surfaced as `502 Bad Gateway` to the merchant.

### In-Memory Storage

Payments are stored in a `HashMap` keyed by UUID. This is intentionally simple for the scope of this challenge. In production, this would be replaced with a persistent store (e.g., PostgreSQL for durability) and a distributed cache (e.g., Redis) for horizontal scaling.

With an external database, `GET /payment/{id}` would introduce an additional failure surface beyond "not found" — connection failures, timeouts, and transient errors. These must be handled as a distinct error category and must not be conflated with the payment's own `message` field (which carries the rejection reason set at processing time and should never be modified after). A `PaymentRepositoryException` mapped to `503 Service Unavailable` via `CommonExceptionHandler` would be the appropriate pattern, keeping business outcomes and infrastructure failures in separate response types.

### RestTemplate

`RestTemplate` is used for synchronous HTTP calls to the bank. A 10-second connect and read timeout is configured. `WebClient` (reactive) was not used as it adds unnecessary complexity for a synchronous request-response flow.

## Project Structure

```
src/main/java/com/checkout/payment/gateway/
├── client/
│   ├── BankClient.java                 interface — decouples service from HTTP transport
│   └── MountebankBankClient.java       RestTemplate implementation
├── configuration/
│   └── ApplicationConfiguration.java  RestTemplate bean with timeouts
├── controller/
│   └── PaymentGatewayController.java
├── enums/
│   └── PaymentStatus.java
├── exception/
│   ├── BankUnavailableException.java
│   ├── CommonExceptionHandler.java     @ControllerAdvice
│   └── EventProcessingException.java
├── model/
│   ├── BankPaymentRequest.java         sent to acquiring bank
│   ├── BankPaymentResponse.java        received from acquiring bank
│   ├── ErrorResponse.java
│   ├── PostPaymentRequest.java         merchant POST request
│   └── PostPaymentResponse.java        merchant response (GET and POST)
├── repository/
│   └── PaymentsRepository.java         in-memory HashMap
├── service/
│   └── PaymentGatewayService.java
├── validation/
│   └── PaymentRequestValidator.java
└── PaymentGatewayApplication.java
```
