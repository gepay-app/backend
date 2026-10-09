package com.gepe.gepay.payment.internal.repository;

import com.gepe.gepay.payment.api.enums.FeeType;
import com.gepe.gepay.payment.internal.entity.UserFeeOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserFeeOverrideRepository extends JpaRepository<UserFeeOverride, Long> {

    @Query("""
        SELECT u FROM UserFeeOverride u
        WHERE u.userId = :userId
          AND u.feeType = :feeType
          AND (:productType IS NULL OR u.productType = :productType)
          AND u.effectiveFrom <= :now
          AND (u.effectiveTo IS NULL OR u.effectiveTo > :now)
        ORDER BY u.effectiveFrom DESC
        LIMIT 1
    """)
    Optional<UserFeeOverride> findActiveOverride(
            @Param("userId") UUID userId,
            @Param("feeType") FeeType feeType,
            @Param("productType") String productType,
            @Param("now") Instant now
    );
}