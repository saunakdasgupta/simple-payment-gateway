package com.checkout.payment.gateway.validation;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestValidator {

  public Optional<String> validate(PostPaymentRequest request) {
    if (request.getCardNumber() == null || !request.getCardNumber().matches("\\d{14,19}")) {
      return Optional.of("card_number must be 14-19 numeric characters");
    }
    if (request.getExpiryMonth() < 1 || request.getExpiryMonth() > 12) {
      return Optional.of("expiry_month must be between 1 and 12");
    }
    try {
      YearMonth expiry = YearMonth.of(request.getExpiryYear(), request.getExpiryMonth());
      if (expiry.isBefore(YearMonth.now())) {
        return Optional.of("Card has expired");
      }
    } catch (DateTimeException e) {
      return Optional.of("Invalid expiry date");
    }
    if (request.getCurrency() == null || !request.getCurrency().matches("[A-Za-z]{3}")) {
      return Optional.of("currency must be exactly 3 alphabetic characters");
    }
    if (request.getAmount() <= 0) {
      return Optional.of("amount must be a positive integer");
    }
    if (request.getCvv() == null || !request.getCvv().matches("\\d{3,4}")) {
      return Optional.of("cvv must be 3-4 numeric characters");
    }
    return Optional.empty();
  }
}
