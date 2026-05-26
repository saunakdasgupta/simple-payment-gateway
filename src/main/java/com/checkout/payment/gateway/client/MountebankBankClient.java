package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class MountebankBankClient implements BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(MountebankBankClient.class);

  private final RestTemplate restTemplate;
  private final String bankSimulatorUrl;
  private final CircuitBreaker circuitBreaker;

  public MountebankBankClient(RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankSimulatorUrl,
      CircuitBreakerRegistry circuitBreakerRegistry) {
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
    this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("bankSimulator");
  }

  @Override
  public BankPaymentResponse processPayment(BankPaymentRequest request) {
    try {
      return circuitBreaker.executeSupplier(() -> {
        BankPaymentResponse response = restTemplate.postForObject(
            bankSimulatorUrl + "/payments", request, BankPaymentResponse.class);
        if (response == null) {
          LOG.warn("Bank returned empty response from {}", bankSimulatorUrl);
          throw new BankUnavailableException("Bank returned an empty response");
        }
        return response;
      });
    } catch (CallNotPermittedException e) {
      // Circuit is OPEN — bank is known to be unreachable; fail fast without a network attempt
      LOG.warn("Circuit breaker OPEN — payment request blocked for {}", bankSimulatorUrl);
      throw new BankUnavailableException("Bank is currently unavailable");
    } catch (HttpServerErrorException e) {
      LOG.warn("Bank returned {} from {}", e.getStatusCode(), bankSimulatorUrl);
      throw new BankUnavailableException("Bank is currently unavailable");
    } catch (ResourceAccessException e) {
      LOG.warn("Bank unreachable at {}: {}", bankSimulatorUrl, e.getMessage());
      throw new BankUnavailableException("Bank is currently unavailable");
    } catch (HttpClientErrorException e) {
      // A 4xx from the bank means the gateway sent a malformed request — this is a
      // gateway bug, not a transient bank issue. paymentId is still in MDC here so
      // this log line is fully correlated. Surface as 500, not 502.
      // Production note: configure resilience4j ignoreExceptions for HttpClientErrorException
      // so a gateway bug does not trip the circuit against a healthy bank.
      LOG.error("Bank rejected gateway request with {} at {} — possible field format mismatch: {}",
          e.getStatusCode(), bankSimulatorUrl, e.getMessage());
      throw new RuntimeException("Gateway sent an invalid request to the bank", e);
    }
  }
}
