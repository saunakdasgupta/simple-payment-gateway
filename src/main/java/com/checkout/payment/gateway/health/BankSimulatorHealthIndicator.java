package com.checkout.payment.gateway.health;

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

  public BankSimulatorHealthIndicator(RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankSimulatorUrl) {
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
  }

  @Override
  public Health health() {
    try {
      restTemplate.getForEntity(bankSimulatorUrl + "/payments", String.class);
      return Health.up().withDetail("url", bankSimulatorUrl).build();
    } catch (HttpStatusCodeException e) {
      // Any HTTP response (including 4xx/5xx) means the bank is reachable
      return Health.up().withDetail("url", bankSimulatorUrl).build();
    } catch (ResourceAccessException e) {
      // Connection refused, timeout, or DNS failure — bank is unreachable
      LOG.warn("Bank simulator unreachable at {}: {}", bankSimulatorUrl, e.getMessage());
      return Health.status(DEGRADED)
          .withDetail("url", bankSimulatorUrl)
          .withDetail("reason", "Bank simulator unreachable")
          .build();
    }
  }
}
