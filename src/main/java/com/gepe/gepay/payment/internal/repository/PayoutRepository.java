package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    Optional<Payout> findByProviderReferenceId(String providerReferenceId);

    /** Payout terbaru untuk sebuah withdrawal (dipakai payout job saat retry). */
    Optional<Payout> findFirstByWithdrawalIdOrderByCreatedAtDesc(UUID withdrawalId);
}