package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.internal.entity.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, Long> {
}