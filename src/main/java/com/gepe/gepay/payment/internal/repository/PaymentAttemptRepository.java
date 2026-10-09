package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, UUID> {
    Optional<PaymentAttempt> findByProviderReferenceId(String providerReferenceId);

    Optional<PaymentAttempt> findFirstByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}