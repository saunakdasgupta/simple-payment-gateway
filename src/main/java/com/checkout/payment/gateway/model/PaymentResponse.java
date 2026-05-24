package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public class PaymentResponse {

  private final UUID id;
  private final PaymentStatus status;
  private final int cardNumberLastFour;
  private final int expiryMonth;
  private final int expiryYear;
  private final String currency;
  private final int amount;

  @Schema(description = "Reason for rejection. Only present when status is Rejected.")
  private final String message;

  public PaymentResponse(UUID id, PaymentStatus status, int cardNumberLastFour,
      int expiryMonth, int expiryYear, String currency, int amount, String message) {
    this.id = id;
    this.status = status;
    this.cardNumberLastFour = cardNumberLastFour;
    this.expiryMonth = expiryMonth;
    this.expiryYear = expiryYear;
    this.currency = currency;
    this.amount = amount;
    this.message = message;
  }

  public UUID getId() {
    return id;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public int getCardNumberLastFour() {
    return cardNumberLastFour;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public int getAmount() {
    return amount;
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getMessage() {
    return message;
  }

  @Override
  public String toString() {
    return "PaymentResponse{" +
        "id=" + id +
        ", status=" + status +
        ", cardNumberLastFour=" + cardNumberLastFour +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", message='" + message + '\'' +
        '}';
  }
}
