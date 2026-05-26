package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class MountebankBankClientTest {

  @Mock private RestTemplate restTemplate;

  private static final String BANK_URL = "http://localhost:8080";
  private final CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
  private MountebankBankClient client;

  private final BankPaymentRequest anyRequest =
      new BankPaymentRequest("2222405343248877", "12/2027", "GBP", 100, "123");

  @BeforeEach
  void setUp() {
    // Reset to CLOSED with empty metrics so each test starts from a known state
    circuitBreakerRegistry.circuitBreaker("bankSimulator").reset();
    client = new MountebankBankClient(restTemplate, BANK_URL, circuitBreakerRegistry);
  }

  @Test
  void shouldPostToCorrectUrl() {
    doReturn(new BankPaymentResponse(true, "auth-code"))
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    client.processPayment(anyRequest);

    verify(restTemplate).postForObject(eq(BANK_URL + "/payments"), any(), eq(BankPaymentResponse.class));
  }

  @Test
  void shouldReturnAuthorizedResponseFromBank() {
    doReturn(new BankPaymentResponse(true, "auth-code-123"))
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    BankPaymentResponse response = client.processPayment(anyRequest);

    assertThat(response.authorized()).isTrue();
    assertThat(response.authorizationCode()).isEqualTo("auth-code-123");
  }

  @Test
  void shouldReturnDeclinedResponseFromBank() {
    doReturn(new BankPaymentResponse(false, ""))
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    BankPaymentResponse response = client.processPayment(anyRequest);

    assertThat(response.authorized()).isFalse();
  }

  @Test
  void shouldThrowBankUnavailableExceptionWhenBankReturns503() {
    doThrow(HttpServerErrorException.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, null, null))
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    assertThatThrownBy(() -> client.processPayment(anyRequest))
        .isInstanceOf(BankUnavailableException.class)
        .hasMessageContaining("unavailable");
  }

  @Test
  void shouldThrowBankUnavailableExceptionWhenBankReturnsNullBody() {
    doReturn(null)
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    assertThatThrownBy(() -> client.processPayment(anyRequest))
        .isInstanceOf(BankUnavailableException.class)
        .hasMessageContaining("empty response");
  }

  @Test
  void shouldThrowBankUnavailableExceptionWhenBankReturns500() {
    doThrow(HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null))
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    assertThatThrownBy(() -> client.processPayment(anyRequest))
        .isInstanceOf(BankUnavailableException.class)
        .hasMessageContaining("unavailable");
  }

  @Test
  void shouldThrowRuntimeExceptionWhenBankReturns400() {
    // A 4xx from the bank means the gateway built a malformed request — gateway bug, not
    // a bank availability issue. Must NOT surface as BankUnavailableException (502).
    doThrow(HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST, "Bad Request", null, null, null))
        .when(restTemplate).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));

    assertThatThrownBy(() -> client.processPayment(anyRequest))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(BankUnavailableException.class);
  }

  @Test
  void shouldThrowBankUnavailableExceptionWhenCircuitIsOpen() {
    // When the circuit is OPEN the bank is known to be unreachable.
    // processPayment must fail fast — CallNotPermittedException → BankUnavailableException.
    circuitBreakerRegistry.circuitBreaker("bankSimulator").transitionToOpenState();

    assertThatThrownBy(() -> client.processPayment(anyRequest))
        .isInstanceOf(BankUnavailableException.class);

    // Bank must never be called when the circuit is OPEN
    verify(restTemplate, never()).postForObject(any(String.class), any(), eq(BankPaymentResponse.class));
  }
}
