package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class MountebankBankClient implements BankClient {

  private static final Logger LOG = LoggerFactory.getLogger(MountebankBankClient.class);

  private final RestTemplate restTemplate;
  private final String bankSimulatorUrl;

  public MountebankBankClient(RestTemplate restTemplate,
      @Value("${bank.simulator.url}") String bankSimulatorUrl) {
    this.restTemplate = restTemplate;
    this.bankSimulatorUrl = bankSimulatorUrl;
  }

  @Override
  public BankPaymentResponse processPayment(BankPaymentRequest request) {
    try {
      BankPaymentResponse response = restTemplate.postForObject(
          bankSimulatorUrl + "/payments", request, BankPaymentResponse.class);
      if (response == null) {
        LOG.warn("Bank returned empty response from {}", bankSimulatorUrl);
        throw new BankUnavailableException("Bank returned an empty response");
      }
      return response;
    } catch (HttpServerErrorException e) {
      LOG.warn("Bank returned {} from {}", e.getStatusCode(), bankSimulatorUrl);
      throw new BankUnavailableException("Bank is currently unavailable");
    }
  }
}
