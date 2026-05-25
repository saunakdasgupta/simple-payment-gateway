package com.checkout.payment.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.time.YearMonth;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // Shorter wait so the OPEN → HALF_OPEN transition doesn't stall the test suite
        "resilience4j.circuitbreaker.instances.bankSimulator.waitDurationInOpenState=2s",
        // Explicit window size and threshold (same as production, but named here for clarity)
        "resilience4j.circuitbreaker.instances.bankSimulator.slidingWindowSize=3",
        "resilience4j.circuitbreaker.instances.bankSimulator.failureRateThreshold=100",
        // Disable background transition thread — the circuit moves to HALF_OPEN on the next
        // incoming call once waitDurationInOpenState has elapsed, which is easier to control in tests
        "resilience4j.circuitbreaker.instances.bankSimulator.automaticTransitionFromOpenToHalfOpenEnabled=false"
    }
)
class PaymentGatewayIntegrationTest {

  // ---- WireMock (bank simulator stub) ----

  private static final WireMockServer wireMock =
      new WireMockServer(wireMockConfig().dynamicPort());

  @DynamicPropertySource
  static void bankSimulatorUrl(DynamicPropertyRegistry registry) {
    wireMock.start();
    registry.add("bank.simulator.url", wireMock::baseUrl);
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  // ---- Test infrastructure ----

  @LocalServerPort
  int port;

  @Autowired
  CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    wireMock.resetAll();
    // Reset to CLOSED with empty metrics so each test starts from a clean slate
    circuitBreakerRegistry.circuitBreaker("bankSimulator").reset();
  }

  // ---- POST /payments: payment processing ----

  @Test
  void shouldReturn200WithAuthorizedStatusWhenBankAuthorizesPayment() {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(okJson("{\"authorized\":true,\"authorization_code\":\"auth-123\"}")));

    String id = given()
        .contentType(ContentType.JSON)
        .body(validPaymentRequest())
    .when()
        .post("/payments")
    .then()
        .statusCode(200)
        .body("status", is("Authorized"))
        .body("id", notNullValue())
        .body("cardNumberLastFour", is("8877"))
        .body("currency", is("GBP"))
        .body("amount", is(100))
        .body("message", nullValue())
        .extract().path("id");

    given().when().get("/payments/" + id)
        .then().statusCode(200).body("status", is("Authorized"));
  }

  @Test
  void shouldReturn200WithDeclinedStatusWhenBankDeclinesPayment() {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(okJson("{\"authorized\":false,\"authorization_code\":\"\"}")));

    String id = given()
        .contentType(ContentType.JSON)
        .body(validPaymentRequest())
    .when()
        .post("/payments")
    .then()
        .statusCode(200)
        .body("status", is("Declined"))
        .body("message", nullValue())
        .extract().path("id");

    given().when().get("/payments/" + id)
        .then().statusCode(200).body("status", is("Declined"));
  }

  @Test
  void shouldReturn400WithRejectedStatusWhenValidationFails() {
    // Rejected payments are assigned a UUID and persisted so merchants can retrieve the
    // outcome by ID for auditing — even though the bank was never called.
    String id = given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "card_number": "not-a-card",
              "expiry_month": 12,
              "expiry_year": 2030,
              "currency": "GBP",
              "amount": 100,
              "cvv": "123"
            }
            """)
    .when()
        .post("/payments")
    .then()
        .statusCode(400)
        .body("status", is("Rejected"))
        .body("message", notNullValue())
        .extract().path("id");

    wireMock.verify(0, postRequestedFor(urlEqualTo("/payments")));

    given().when().get("/payments/" + id)
        .then().statusCode(200).body("status", is("Rejected"));
  }

  @Test
  void shouldReturn400WithRejectedStatusWhenCardIsExpired() {
    String id = given()
        .contentType(ContentType.JSON)
        .body("""
            {
              "card_number": "2222405343248877",
              "expiry_month": 1,
              "expiry_year": 2020,
              "currency": "GBP",
              "amount": 100,
              "cvv": "123"
            }
            """)
    .when()
        .post("/payments")
    .then()
        .statusCode(400)
        .body("status", is("Rejected"))
        .body("message", notNullValue())
        .extract().path("id");

    wireMock.verify(0, postRequestedFor(urlEqualTo("/payments")));

    given().when().get("/payments/" + id)
        .then().statusCode(200).body("status", is("Rejected"));
  }

  @Test
  void shouldReturn400WhenRequestBodyIsMissing() {
    given()
        .contentType(ContentType.JSON)
    .when()
        .post("/payments")
    .then()
        .statusCode(400);
  }

  @Test
  void shouldReturn502WhenBankReturns503() {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(serviceUnavailable()));

    given()
        .contentType(ContentType.JSON)
        .body(validPaymentRequest())
    .when()
        .post("/payments")
    .then()
        .statusCode(502)
        .body("message", notNullValue());
  }

  @Test
  void shouldReturn502WhenBankReturns500() {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(serverError()));

    given()
        .contentType(ContentType.JSON)
        .body(validPaymentRequest())
    .when()
        .post("/payments")
    .then()
        .statusCode(502)
        .body("message", notNullValue());
  }

  @Test
  void cardNumberLastFourShouldSerializeAsStringNotInteger() {
    // Verifies that JSON output is "8877" (string) not 8877 (number).
    // This catches a regression if cardNumberLastFour is changed back to int.
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(okJson("{\"authorized\":true,\"authorization_code\":\"auth-123\"}")));

    String id = given()
        .contentType(ContentType.JSON)
        .body(validPaymentRequest())
    .when()
        .post("/payments")
    .then()
        .statusCode(200)
        .body("cardNumberLastFour", is("8877"))
        .body("cardNumberLastFour", instanceOf(String.class))
        .extract().path("id");

    // Also verify the string type survives the repository round-trip
    given().when().get("/payments/" + id)
        .then().statusCode(200)
        .body("cardNumberLastFour", is("8877"))
        .body("cardNumberLastFour", instanceOf(String.class));
  }

  // ---- GET /payments/{id} ----

  @Test
  void shouldReturnStoredPaymentById() {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(okJson("{\"authorized\":true,\"authorization_code\":\"auth-123\"}")));

    // POST a payment and capture the assigned ID
    String id = given()
        .contentType(ContentType.JSON)
        .body(validPaymentRequest())
    .when()
        .post("/payments")
    .then()
        .statusCode(200)
        .extract().path("id");

    // GET it back — verifies the in-memory repository round-trip
    given()
    .when()
        .get("/payments/" + id)
    .then()
        .statusCode(200)
        .body("id", is(id))
        .body("status", is("Authorized"))
        .body("cardNumberLastFour", is("8877"))
        .body("currency", is("GBP"))
        .body("amount", is(100));
  }

  @Test
  void shouldReturn404WhenPaymentIdDoesNotExist() {
    given()
    .when()
        .get("/payments/" + java.util.UUID.randomUUID())
    .then()
        .statusCode(404)
        .body("message", is("Payment not found"));
  }

  @Test
  void shouldReturn400WhenPaymentIdIsNotAValidUuid() {
    given()
    .when()
        .get("/payments/not-a-uuid")
    .then()
        .statusCode(400);
  }

  // ---- Circuit breaker: CLOSED → OPEN → CLOSED ----

  @Test
  void circuitBreakerShouldTransitionThroughFullCycle() throws InterruptedException {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bankSimulator");

    // 1. Circuit starts CLOSED
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

    // 2. Bank is unreachable on every health probe
    wireMock.stubFor(get(urlEqualTo("/payments"))
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    // 3. Three consecutive failures fill the sliding window (size=3, threshold=100%)
    //    → circuit trips to OPEN
    for (int i = 0; i < 3; i++) {
      given().when().get("/actuator/health/bankSimulator")
          .then().statusCode(200).body("status", is("DEGRADED"));
    }
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // 4. Circuit OPEN: health check fast-fails without touching the bank
    wireMock.resetAll(); // Any call to WireMock here would be an unexpected probe

    given().when().get("/actuator/health/bankSimulator")
        .then()
        .statusCode(200)
        .body("status", is("DEGRADED"))
        .body("details.circuitBreaker", is("OPEN"));

    wireMock.verify(0, getRequestedFor(urlEqualTo("/payments")));

    // 5. Wait for waitDurationInOpenState (2s) to elapse
    //    With automaticTransitionFromOpenToHalfOpenEnabled=false, the circuit stays
    //    in OPEN state until the next call arrives — at which point it transitions
    //    to HALF_OPEN and permits one probe through.
    Thread.sleep(2500);

    // 6. Bank recovers — stub returns 200
    wireMock.stubFor(get(urlEqualTo("/payments")).willReturn(ok()));

    // The first health check after the wait triggers the OPEN → HALF_OPEN → CLOSED transition:
    //   - executeRunnable sees OPEN + wait elapsed → transitions to HALF_OPEN, permits this call
    //   - probe() succeeds → circuitBreaker.onSuccess() → transitions to CLOSED
    given().when().get("/actuator/health/bankSimulator")
        .then()
        .statusCode(200)
        .body("status", is("UP"));

    // 7. Circuit is back to CLOSED
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

    // Bank was probed exactly once during recovery (the single HALF_OPEN trial call)
    wireMock.verify(1, getRequestedFor(urlEqualTo("/payments")));
  }

  // ---- Helpers ----

  private String validPaymentRequest() {
    int expiryYear = YearMonth.now().plusYears(1).getYear();
    return """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": %d,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """.formatted(expiryYear);
  }
}
