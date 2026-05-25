package com.checkout.payment.gateway.repository;

import com.checkout.payment.gateway.model.PaymentResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

  private final Map<UUID, PaymentResponse> payments = new ConcurrentHashMap<>();

  @Override
  public void add(PaymentResponse payment) {
    payments.put(payment.getId(), payment);
  }

  @Override
  public Optional<PaymentResponse> get(UUID id) {
    return Optional.ofNullable(payments.get(id));
  }
}
