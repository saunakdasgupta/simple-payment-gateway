package com.checkout.payment.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.BankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.checkout.payment.gateway.validation.PaymentRequestValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

  @Mock private PaymentsRepository paymentsRepository;
  @Mock private BankClient bankClient;
  @Mock private PaymentRequestValidator validator;

  @InjectMocks private PaymentGatewayService service;

  private PostPaymentRequest requestFor(String cardNumber) {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber(cardNumber);
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(100);
    request.setCvv("123");
    return request;
  }

  // --- processPayment: rejected ---

  @Test
  void shouldReturnRejectedStatusWhenValidationFails() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.of("card_number must be 14-19 numeric characters"));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.REJECTED);
  }

  @Test
  void shouldIncludeValidationReasonInMessageWhenRejected() {
    PostPaymentRequest request = requestFor("2222405343248877");
    String reason = "card_number must be 14-19 numeric characters";
    when(validator.validate(request)).thenReturn(Optional.of(reason));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getMessage()).isEqualTo(reason);
  }

  @Test
  void shouldNotCallBankWhenValidationFails() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.of("amount must be a positive integer"));

    service.processPayment(request);

    verify(bankClient, never()).processPayment(any());
  }

  @Test
  void shouldAssignIdToRejectedPayment() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.of("Card has expired"));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getId()).isNotNull();
  }

  @Test
  void shouldStoreRejectedPaymentInRepository() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.of("Card has expired"));

    service.processPayment(request);

    ArgumentCaptor<PaymentResponse> captor = ArgumentCaptor.forClass(PaymentResponse.class);
    verify(paymentsRepository).add(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REJECTED);
  }

  // --- processPayment: authorized ---

  @Test
  void shouldReturnAuthorizedWhenBankAuthorizesPayment() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code-123"));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void shouldNotIncludeMessageWhenAuthorized() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code-123"));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getMessage()).isNull();
  }

  // --- processPayment: declined ---

  @Test
  void shouldReturnDeclinedWhenBankDeclinesPayment() {
    PostPaymentRequest request = requestFor("2222405343248872");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(false, ""));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
  }

  @Test
  void shouldNotIncludeMessageWhenDeclined() {
    PostPaymentRequest request = requestFor("2222405343248872");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(false, ""));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getMessage()).isNull();
  }

  // --- processPayment: card masking ---

  @Test
  void shouldExtractLastFourDigitsFromCardNumber() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getCardNumberLastFour()).isEqualTo(8877);
  }

  @Test
  void shouldDefaultCardNumberLastFourToZeroWhenCardNumberIsInvalid() {
    PostPaymentRequest request = requestFor("abc");
    when(validator.validate(request)).thenReturn(Optional.of("card_number must be 14-19 numeric characters"));

    PaymentResponse response = service.processPayment(request);

    assertThat(response.getCardNumberLastFour()).isEqualTo(0);
  }

  // --- processPayment: bank request formatting ---

  @Test
  void shouldFormatExpiryDateAsMmYyyyInBankRequest() {
    PostPaymentRequest request = requestFor("2222405343248877");
    request.setExpiryMonth(12);
    request.setExpiryYear(2027);
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    service.processPayment(request);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankClient).processPayment(captor.capture());
    assertThat(captor.getValue().expiryDate()).isEqualTo("12/2027");
  }

  @Test
  void shouldZeroPadSingleDigitMonthInBankRequest() {
    PostPaymentRequest request = requestFor("2222405343248877");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    service.processPayment(request);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankClient).processPayment(captor.capture());
    assertThat(captor.getValue().expiryDate()).isEqualTo("04/2027");
  }

  // --- processPayment: storage ---

  @Test
  void shouldStoreAuthorizedPaymentInRepository() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenReturn(new BankPaymentResponse(true, "auth-code"));

    PaymentResponse response = service.processPayment(request);

    ArgumentCaptor<PaymentResponse> captor = ArgumentCaptor.forClass(PaymentResponse.class);
    verify(paymentsRepository).add(captor.capture());
    assertThat(captor.getValue()).isSameAs(response);
  }

  // --- processPayment: bank unavailable ---

  @Test
  void shouldPropagateBankUnavailableException() {
    PostPaymentRequest request = requestFor("2222405343248877");
    when(validator.validate(request)).thenReturn(Optional.empty());
    when(bankClient.processPayment(any())).thenThrow(new BankUnavailableException("Bank is down"));

    assertThatThrownBy(() -> service.processPayment(request))
        .isInstanceOf(BankUnavailableException.class);
  }

  // --- getPaymentById ---

  @Test
  void shouldReturnPaymentWhenFoundById() {
    UUID id = UUID.randomUUID();
    PaymentResponse stored = new PaymentResponse(id, PaymentStatus.AUTHORIZED, 8877, 12, 2027, "GBP", 100, null);
    when(paymentsRepository.get(id)).thenReturn(Optional.of(stored));

    assertThat(service.getPaymentById(id)).isSameAs(stored);
  }

  @Test
  void shouldThrowEventProcessingExceptionWhenPaymentNotFound() {
    UUID id = UUID.randomUUID();
    when(paymentsRepository.get(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getPaymentById(id))
        .isInstanceOf(EventProcessingException.class);
  }
}
