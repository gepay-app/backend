package com.gepe.gepay.donation.internal.repository;

import com.gepe.gepay.donation.internal.entity.Donation;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonationRepository extends JpaRepository<Donation, UUID> {

    Optional<Donation> findByIdempotencyKey(String idempotencyKey);

    Optional<Donation> findByPaymentId(UUID paymentId);

    /** Halaman pertama riwayat donasi creator (keyset by {@code id DESC}). */
    List<Donation> findByCreatorIdOrderByIdDesc(UUID creatorId, Limit limit);

    /** Halaman berikutnya setelah {@code cursor}. */
    List<Donation> findByCreatorIdAndIdLessThanOrderByIdDesc(UUID creatorId, UUID cursor, Limit limit);
}
