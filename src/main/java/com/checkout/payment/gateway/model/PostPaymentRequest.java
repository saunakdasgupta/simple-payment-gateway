package com.checkout.payment.gateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;

@Schema(description = "Card payment request")
public class PostPaymentRequest implements Serializable {

  @Schema(description = "Full card number", example = "2222405343248877",
      minLength = 14, maxLength = 19)
  @JsonProperty("card_number")
  private String cardNumber;

  @Schema(description = "Card expiry month (1–12)", example = "12", minimum = "1", maximum = "12")
  @JsonProperty("expiry_month")
  private int expiryMonth;

  @Schema(description = "Card expiry year (current year or later)", example = "2027")
  @JsonProperty("expiry_year")
  private int expiryYear;

  @Schema(description = "ISO 4217 currency code (3 alphabetic characters)", example = "GBP")
  private String currency;

  @Schema(description = "Payment amount in minor currency units (e.g. pence, cents)",
      example = "100", minimum = "1")
  private int amount;

  @Schema(description = "Card security code (3–4 numeric characters)", example = "123")
  private String cvv;

  public String getCardNumber() {
    return cardNumber;
  }

  public void setCardNumber(String cardNumber) {
    this.cardNumber = cardNumber;
  }

  public int getExpiryMonth() {
    return expiryMonth;
  }

  public void setExpiryMonth(int expiryMonth) {
    this.expiryMonth = expiryMonth;
  }

  public int getExpiryYear() {
    return expiryYear;
  }

  public void setExpiryYear(int expiryYear) {
    this.expiryYear = expiryYear;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public int getAmount() {
    return amount;
  }

  public void setAmount(int amount) {
    this.amount = amount;
  }

  public String getCvv() {
    return cvv;
  }

  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  @Override
  public String toString() {
    String maskedCard = cardNumber != null && cardNumber.length() >= 4
        ? "****" + cardNumber.substring(cardNumber.length() - 4)
        : "****";
    return "PostPaymentRequest{" +
        "cardNumber='" + maskedCard + '\'' +
        ", expiryMonth=" + expiryMonth +
        ", expiryYear=" + expiryYear +
        ", currency='" + currency + '\'' +
        ", amount=" + amount +
        '}';
  }
}
