package com.checkout.payment.gateway.health;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class BankSimulatorHealthIndicator implements HealthIndicator {

  private static final Logger LOG = LoggerFactory.getLogger(BankSimulatorHealthIndicator.class);
  private static final Status DEGRADED = new Status("DEGRADED");

  private final RestTemplate restTemplate;
  private final String bankSimulatorUrl;
  private final CircuitBreaker circuitBreaker;

  public BankSimulatorHealthIndicator(RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankSimulatorUrl,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("bankSimulator");
  }

  @Override
  public Health health() {
    try {
      circuitBreaker.executeRunnable(this::probe);
      return Health.up()
          .withDetail("url", bankSimulatorUrl)
          .withDetail("circuitBreaker", circuitBreaker.getState().name())
          .build();
    } catch (CallNotPermittedException e) {
      // Circuit is OPEN — skip probing; bank is known to be unreachable
      return Health.status(DEGRADED)
          .withDetail("url", bankSimulatorUrl)
          .withDetail("reason", "Bank simulator unreachable")
          .withDetail("circuitBreaker", "OPEN")
          .build();
    } catch (ResourceAccessException e) {
      LOG.warn("Bank simulator unreachable at {}: {}", bankSimulatorUrl, e.getMessage());
      return Health.status(DEGRADED)
          .withDetail("url", bankSimulatorUrl)
          .withDetail("reason", "Bank simulator unreachable")
          .withDetail("circuitBreaker", circuitBreaker.getState().name())
          .build();
    }
  }

  /**
   * Probes the bank by sending a GET request.
   * Any HTTP response (including 4xx/5xx) means the bank process is running.
   * Only ResourceAccessException (connection refused, timeout) is treated as a failure.
   */
  private void probe() {
    try {
      restTemplate.getForEntity(bankSimulatorUrl + "/payments", String.class);
    } catch (HttpStatusCodeException e) {
      // Any HTTP response means the bank is reachable — swallow and record as success
    }
    // ResourceAccessException propagates → circuit breaker records it as a failure
  }
}
