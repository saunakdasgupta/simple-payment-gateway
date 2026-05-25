package com.checkout.payment.gateway.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class BankSimulatorHealthIndicatorTest {

  @Mock private RestTemplate restTemplate;

  private static final String BANK_URL = "http://localhost:8080";
  private CircuitBreakerRegistry circuitBreakerRegistry;
  private BankSimulatorHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
    indicator = new BankSimulatorHealthIndicator(restTemplate, BANK_URL, circuitBreakerRegistry);
  }

  @Test
  void shouldReturnUpWhenBankResponds() {
    when(restTemplate.getForEntity(eq(BANK_URL + "/payments"), eq(String.class)))
        .thenReturn(ResponseEntity.ok(""));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("url", BANK_URL);
  }

  @Test
  void shouldReturnUpWhenBankRespondsWithHttpErrorStatus() {
    // Any HTTP response (even 4xx) means the bank is reachable
    when(restTemplate.getForEntity(eq(BANK_URL + "/payments"), eq(String.class)))
        .thenThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReturnUpWhenBankRespondsWithServerErrorStatus() {
    // 5xx means the bank process is running but returned an error — still reachable
    when(restTemplate.getForEntity(eq(BANK_URL + "/payments"), eq(String.class)))
        .thenThrow(HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, null, null));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldReturnDegradedWhenBankIsUnreachable() {
    when(restTemplate.getForEntity(eq(BANK_URL + "/payments"), eq(String.class)))
        .thenThrow(new ResourceAccessException("Connection refused"));

    Health health = indicator.health();

    assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
    assertThat(health.getDetails()).containsEntry("url", BANK_URL);
    assertThat(health.getDetails()).containsEntry("reason", "Bank simulator unreachable");
  }

  @Test
  void shouldReturnDegradedImmediatelyWhenCircuitIsOpen() {
    // Force circuit open by recording enough failures
    CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("bankSimulator");
    // The default sliding window is 100; use transitionToOpenState() to open it directly
    circuitBreaker.transitionToOpenState();

    // Even if the RestTemplate were to be called, we don't want it called — circuit is open
    Health health = indicator.health();

    assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
    assertThat(health.getDetails()).containsEntry("circuitBreaker", "OPEN");
    verify(restTemplate, never()).getForEntity(eq(BANK_URL + "/payments"), eq(String.class));
  }
}
