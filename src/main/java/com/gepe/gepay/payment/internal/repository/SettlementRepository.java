package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SettlementRepository extends JpaRepository<Settlement, UUID> {
}