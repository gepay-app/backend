package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.FeeType;

import java.time.Instant;
import java.util.UUID;

/**
 * Baris baru tiap perubahan rate — jangan update baris lama.
 */
public record UserFeeOverrideCreateRequest(
        UUID userId,
        FeeType feeType,
        String productType,
        Long fixedAmount,
        Integer percentageBps,
        Integer vatBps,
        Instant effectiveFrom,
        String reason,
        UUID approvedBy
) {
}