package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.PaymentNotFoundException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentRepository paymentRepository;
  private final BankClient bankClient;
  private final PaymentRequestValidator validator;
  private final MeterRegistry meterRegistry;

  public PaymentGatewayService(PaymentRepository paymentRepository,
      BankClient bankClient,
      PaymentRequestValidator validator,
      MeterRegistry meterRegistry) {
    this.paymentRepository = paymentRepository;
    this.bankClient = bankClient;
    this.validator = validator;
    this.meterRegistry = meterRegistry;
  }

  public PaymentResponse getPaymentById(UUID id) {
    MDC.put("paymentId", id.toString());
    try {
      LOG.info("Retrieving payment with ID {}", id);
      return paymentRepository.get(id).orElseThrow(() -> {
        LOG.warn("Payment not found for ID: {}", id);
        return new PaymentNotFoundException("Payment not found");
      });
    } finally {
      MDC.remove("paymentId");
    }
  }

  public PaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    UUID paymentId = UUID.randomUUID();
    MDC.put("paymentId", paymentId.toString());
    try {
      return doProcessPayment(paymentId, paymentRequest);
    } finally {
      MDC.remove("paymentId");
    }
  }

  private PaymentResponse doProcessPayment(UUID paymentId, PostPaymentRequest paymentRequest) {
    Optional<String> validationError = validator.validate(paymentRequest);
    if (validationError.isPresent()) {
      LOG.info("Payment {} rejected: {}", paymentId, validationError.get());
      meterRegistry.counter("payments.processed", "status", PaymentStatus.REJECTED.getName()).increment();
      return buildAndStore(paymentId, paymentRequest, PaymentStatus.REJECTED, validationError.get());
    }

    LOG.info("Processing payment {} for card ****{}, amount {} {}",
        paymentId,
        paymentRequest.getCardNumber().substring(paymentRequest.getCardNumber().length() - 4),
        paymentRequest.getAmount(),
        paymentRequest.getCurrency());

    BankPaymentRequest bankRequest = new BankPaymentRequest(
        paymentRequest.getCardNumber(),
        String.format("%02d/%d", paymentRequest.getExpiryMonth(), paymentRequest.getExpiryYear()),
        paymentRequest.getCurrency(),
        paymentRequest.getAmount(),
        paymentRequest.getCvv()
    );

    BankPaymentResponse bankResponse = bankClient.processPayment(bankRequest);
    PaymentStatus status = bankResponse.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
    LOG.info("Payment {} {}", paymentId, status.getName());
    meterRegistry.counter("payments.processed", "status", status.getName()).increment();

    return buildAndStore(paymentId, paymentRequest, status, null);
  }

  private PaymentResponse buildAndStore(UUID paymentId, PostPaymentRequest request,
      PaymentStatus status, String message) {
    PaymentResponse response = new PaymentResponse(
        paymentId,
        status,
        request.getCardNumberLastFour(),  // card masking logic moved to PostPaymentRequest
        request.getExpiryMonth(),
        request.getExpiryYear(),
        request.getCurrency(),
        request.getAmount(),
        message
    );
    paymentRepository.add(response);
    return response;
  }
}
