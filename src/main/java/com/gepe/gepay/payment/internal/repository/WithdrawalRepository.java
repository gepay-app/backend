package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.api.enums.WithdrawalStatus;
import com.gepe.gepay.payment.internal.entity.Withdrawal;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Optional<Withdrawal> findByIdempotencyKey(String idempotencyKey);

    /** Halaman pertama riwayat penarikan user (keyset by {@code id DESC}). */
    List<Withdrawal> findByUserIdOrderByIdDesc(UUID userId, Limit limit);

    /** Halaman berikutnya setelah {@code cursor}. */
    List<Withdrawal> findByUserIdAndIdLessThanOrderByIdDesc(UUID userId, UUID cursor, Limit limit);

    /** Kandidat payout job: masih menunggu diproses (REQUESTED) atau sedang digarap (PROCESSING). */
    List<Withdrawal> findByStatusInOrderByCreatedAtAsc(Collection<WithdrawalStatus> statuses);
}