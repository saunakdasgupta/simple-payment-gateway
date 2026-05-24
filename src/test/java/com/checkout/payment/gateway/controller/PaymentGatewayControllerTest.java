package com.checkout.payment.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.service.PaymentGatewayService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentGatewayController.class)
class PaymentGatewayControllerTest {

  @Autowired private MockMvc mvc;
  @MockBean  private PaymentGatewayService paymentGatewayService;

  // --- GET ---

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = buildResponse(UUID.randomUUID(), PaymentStatus.AUTHORIZED, 8877, null);
    when(paymentGatewayService.getPaymentById(payment.getId())).thenReturn(payment);

    mvc.perform(get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(payment.getId().toString()))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2027))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    UUID id = UUID.randomUUID();
    when(paymentGatewayService.getPaymentById(id)).thenThrow(new EventProcessingException("Invalid ID"));

    mvc.perform(get("/payment/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  // --- POST: authorized ---

  @Test
  void shouldReturn200WhenPaymentIsAuthorized() throws Exception {
    when(paymentGatewayService.processPayment(any()))
        .thenReturn(buildResponse(UUID.randomUUID(), PaymentStatus.AUTHORIZED, 8877, null));

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.cardNumberLastFour").value(8877))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  // --- POST: declined ---

  @Test
  void shouldReturn200WhenPaymentIsDeclined() throws Exception {
    when(paymentGatewayService.processPayment(any()))
        .thenReturn(buildResponse(UUID.randomUUID(), PaymentStatus.DECLINED, 8872, null));

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"))
        .andExpect(jsonPath("$.message").doesNotExist());
  }

  // --- POST: rejected ---

  @Test
  void shouldReturn400WhenPaymentIsRejected() throws Exception {
    String reason = "card_number must be 14-19 numeric characters";
    when(paymentGatewayService.processPayment(any()))
        .thenReturn(buildResponse(UUID.randomUUID(), PaymentStatus.REJECTED, 0, reason));

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentRequest()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value("Rejected"))
        .andExpect(jsonPath("$.message").value(reason));
  }

  // --- POST: bank unavailable ---

  @Test
  void shouldReturn502WhenBankIsUnavailable() throws Exception {
    when(paymentGatewayService.processPayment(any()))
        .thenThrow(new BankUnavailableException("Bank down"));

    mvc.perform(post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content(validPaymentRequest()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  private PostPaymentResponse buildResponse(UUID id, PaymentStatus status, int lastFour,
      String message) {
    PostPaymentResponse response = new PostPaymentResponse();
    response.setId(id);
    response.setStatus(status);
    response.setCardNumberLastFour(lastFour);
    response.setExpiryMonth(12);
    response.setExpiryYear(2027);
    response.setCurrency("GBP");
    response.setAmount(100);
    response.setMessage(message);
    return response;
  }

  private String validPaymentRequest() {
    return """
        {
          "card_number": "2222405343248877",
          "expiry_month": 12,
          "expiry_year": 2027,
          "currency": "GBP",
          "amount": 100,
          "cvv": "123"
        }
        """;
  }
}
