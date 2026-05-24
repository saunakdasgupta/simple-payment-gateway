package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.BankPaymentRequest;
import com.checkout.payment.gateway.model.BankPaymentResponse;

public interface BankClient {

  BankPaymentResponse processPayment(BankPaymentRequest request);
}
