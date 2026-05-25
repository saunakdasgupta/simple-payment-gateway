package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentResponse;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

  void add(PaymentResponse payment);

  Optional<PaymentResponse> get(UUID id);
}
