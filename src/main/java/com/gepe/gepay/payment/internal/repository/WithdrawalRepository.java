package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.api.enums.WithdrawalStatus;
import com.gepe.gepay.payment.internal.entity.Withdrawal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Optional<Withdrawal> findByIdempotencyKey(String idempotencyKey);

    List<Withdrawal> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Kandidat payout job: masih menunggu diproses (REQUESTED) atau sedang digarap (PROCESSING). */
    List<Withdrawal> findByStatusInOrderByCreatedAtAsc(Collection<WithdrawalStatus> statuses);
}