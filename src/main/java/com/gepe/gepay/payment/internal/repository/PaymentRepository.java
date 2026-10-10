package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.internal.entity.Payment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Halaman pertama earnings milik seorang user (keyset by {@code id DESC},
     * memakai index {@code (user_id, id DESC)}). Ambil {@code limit} baris.
     */
    List<Payment> findByUserIdOrderByIdDesc(UUID userId, Limit limit);

    /** Halaman berikutnya setelah {@code cursor} (id terakhir halaman sebelumnya). */
    List<Payment> findByUserIdAndIdLessThanOrderByIdDesc(UUID userId, UUID cursor, Limit limit);

    /**
     * Kandidat settlement otomatis: sudah {@code PAID}, belum masuk batch mana pun,
     * dan {@code expected_settlement_date} sudah lewat. {@code channelRoute}
     * ikut di-fetch supaya pengelompokan per {@code settlement_target} tidak N+1.
     */
    @Query("""
        SELECT p FROM Payment p
        JOIN FETCH p.channelRoute r
        WHERE p.status = :status
          AND p.settlementId IS NULL
          AND p.expectedSettlementDate <= :today
        ORDER BY p.providerId, p.id
    """)
    List<Payment> findSettlementCandidates(
            @Param("status") PaymentStatus status,
            @Param("today") LocalDate today
    );
}