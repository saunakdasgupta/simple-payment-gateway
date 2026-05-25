package com.checkout.payment.gateway.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
  private BankSimulatorHealthIndicator indicator;

  @BeforeEach
  void setUp() {
    indicator = new BankSimulatorHealthIndicator(restTemplate, BANK_URL);
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
}
