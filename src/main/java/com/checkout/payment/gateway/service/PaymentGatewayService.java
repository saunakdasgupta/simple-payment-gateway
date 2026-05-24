package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final BankClient bankClient;
  private final PaymentRequestValidator validator;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      BankClient bankClient,
      PaymentRequestValidator validator) {
    this.paymentsRepository = paymentsRepository;
    this.bankClient = bankClient;
    this.validator = validator;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    UUID paymentId = UUID.randomUUID();

    Optional<String> validationError = validator.validate(paymentRequest);
    if (validationError.isPresent()) {
      LOG.info("Payment {} rejected: {}", paymentId, validationError.get());
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

    return buildAndStore(paymentId, paymentRequest, status, null);
  }

  private PostPaymentResponse buildAndStore(UUID paymentId, PostPaymentRequest request,
      PaymentStatus status, String message) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(paymentId);
    response.setStatus(status);
    response.setCardNumberLastFour(safeLastFour(request.getCardNumber()));
    response.setExpiryMonth(request.getExpiryMonth());
    response.setExpiryYear(request.getExpiryYear());
    response.setCurrency(request.getCurrency());
    response.setAmount(request.getAmount());
    response.setMessage(message);
    paymentsRepository.add(response);
    return response;
  }

  private int safeLastFour(String cardNumber) {
    if (cardNumber != null && cardNumber.matches("\\d+") && cardNumber.length() >= 4) {
      return Integer.parseInt(cardNumber.substring(cardNumber.length() - 4));
    }
    return 0;
  }
}
