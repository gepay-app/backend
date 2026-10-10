package com.gepe.gepay.donation.internal.repository;

import com.gepe.gepay.donation.internal.entity.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DonationRepository extends JpaRepository<Donation, UUID> {

    Optional<Donation> findByIdempotencyKey(String idempotencyKey);

    Optional<Donation> findByPaymentId(UUID paymentId);
}
