package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.Adjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdjustmentRepository extends JpaRepository<Adjustment, UUID> {
}