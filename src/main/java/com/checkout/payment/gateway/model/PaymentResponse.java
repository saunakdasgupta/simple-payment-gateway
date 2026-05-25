package com.checkout.payment.gateway.model;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public class PaymentResponse {

  @Schema(description = "Unique payment ID (UUID)", example = "8f433a5a-4317-457f-8bb6-876eeb6c9968")
  private final UUID id;

  @Schema(description = "Payment outcome: Authorized, Declined, or Rejected",
      example = "Authorized")
  private final PaymentStatus status;

  @Schema(description = "Last four digits of the card number as a zero-padded string. "
      + "Null when the card number itself was invalid.", example = "8877")
  private final String cardNumberLastFour;

  @Schema(description = "Card expiry month", example = "12")
  private final int expiryMonth;

  @Schema(description = "Card expiry year", example = "2027")
  private final int expiryYear;

  @Schema(description = "ISO 4217 currency code", example = "GBP")
  private final String currency;

  @Schema(description = "Payment amount in minor currency units", example = "100")
  private final int amount;

  @Schema(description = "Reason for rejection. Only present when status is Rejected.")
  private final String message;

  public PaymentResponse(UUID id, PaymentStatus status, String cardNumberLastFour,
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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public String getCardNumberLastFour() {
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
        ", cardNumberLastFour='" + cardNumberLastFour + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        ", message='" + message + '\'' +
        '}';
  }
}
