package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {
}