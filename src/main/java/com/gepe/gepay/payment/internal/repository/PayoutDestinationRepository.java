package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.PayoutDestination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutDestinationRepository extends JpaRepository<PayoutDestination, UUID> {

    List<PayoutDestination> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PayoutDestination> findByIdAndUserId(UUID id, UUID userId);
}