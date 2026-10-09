package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.api.enums.FeeType;
import com.gepe.gepay.payment.internal.entity.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, Long> {

    @Query("""
        SELECT f FROM FeeConfig f
        WHERE f.feeType = :feeType
          AND (:productType IS NULL OR f.productType = :productType)
          AND (:providerId IS NULL OR f.providerId = :providerId)
          AND (:channelId IS NULL OR f.channelId = :channelId)
          AND f.effectiveFrom <= :now
          AND (f.effectiveTo IS NULL OR f.effectiveTo > :now)
        ORDER BY f.effectiveFrom DESC
        LIMIT 1
    """)
    Optional<FeeConfig> findActiveConfig(
            @Param("feeType") FeeType feeType,
            @Param("productType") String productType,
            @Param("providerId") Long providerId,
            @Param("channelId") Long channelId,
            @Param("now") Instant now
    );
}