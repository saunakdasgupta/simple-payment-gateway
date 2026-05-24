package com.checkout.payment.gateway.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class PaymentRequestValidatorTest {

  private final PaymentRequestValidator validator = new PaymentRequestValidator();

  private PostPaymentRequest validRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(YearMonth.now().plusYears(1).getYear());
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  @Test
  void shouldPassValidationForValidRequest() {
    assertThat(validator.validate(validRequest())).isEmpty();
  }

  // --- Card number ---

  @Test
  void shouldRejectWhenCardNumberIsNull() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber(null);
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCardNumberHasFewerThan14Digits() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234567890123");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldPassWhenCardNumberHasExactly14Digits() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("12345678901234");
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void shouldPassWhenCardNumberHasExactly19Digits() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("1234567890123456789");
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void shouldRejectWhenCardNumberHasMoreThan19Digits() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("12345678901234567890");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCardNumberContainsNonNumericCharacters() {
    PostPaymentRequest request = validRequest();
    request.setCardNumber("2222405343248abc");
    assertThat(validator.validate(request)).isPresent();
  }

  // --- Expiry month ---

  @Test
  void shouldRejectWhenExpiryMonthIsZero() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(0);
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenExpiryMonthIsThirteen() {
    PostPaymentRequest request = validRequest();
    request.setExpiryMonth(13);
    assertThat(validator.validate(request)).isPresent();
  }

  // --- Expiry date (combined) ---

  @Test
  void shouldRejectWhenCardIsExpired() {
    PostPaymentRequest request = validRequest();
    YearMonth lastMonth = YearMonth.now().minusMonths(1);
    request.setExpiryMonth(lastMonth.getMonthValue());
    request.setExpiryYear(lastMonth.getYear());
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldPassWhenExpiryIsCurrentYearAndMonth() {
    PostPaymentRequest request = validRequest();
    YearMonth now = YearMonth.now();
    request.setExpiryMonth(now.getMonthValue());
    request.setExpiryYear(now.getYear());
    assertThat(validator.validate(request)).isEmpty();
  }

  // --- Currency ---

  @Test
  void shouldRejectWhenCurrencyIsNull() {
    PostPaymentRequest request = validRequest();
    request.setCurrency(null);
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCurrencyIsTwoCharacters() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("GB");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCurrencyIsFourCharacters() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("GBPP");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCurrencyContainsDigits() {
    PostPaymentRequest request = validRequest();
    request.setCurrency("G1P");
    assertThat(validator.validate(request)).isPresent();
  }

  // --- Amount ---

  @Test
  void shouldRejectWhenAmountIsZero() {
    PostPaymentRequest request = validRequest();
    request.setAmount(0);
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenAmountIsNegative() {
    PostPaymentRequest request = validRequest();
    request.setAmount(-1);
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldPassWhenAmountIsOne() {
    PostPaymentRequest request = validRequest();
    request.setAmount(1);
    assertThat(validator.validate(request)).isEmpty();
  }

  // --- CVV ---

  @Test
  void shouldRejectWhenCvvIsNull() {
    PostPaymentRequest request = validRequest();
    request.setCvv(null);
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCvvHasTwoDigits() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCvvHasFiveDigits() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12345");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldRejectWhenCvvContainsNonNumericCharacters() {
    PostPaymentRequest request = validRequest();
    request.setCvv("12a");
    assertThat(validator.validate(request)).isPresent();
  }

  @Test
  void shouldPassWhenCvvHasFourDigits() {
    PostPaymentRequest request = validRequest();
    request.setCvv("1234");
    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void shouldPassWhenCvvHasLeadingZero() {
    PostPaymentRequest request = validRequest();
    request.setCvv("099");
    assertThat(validator.validate(request)).isEmpty();
  }
}
