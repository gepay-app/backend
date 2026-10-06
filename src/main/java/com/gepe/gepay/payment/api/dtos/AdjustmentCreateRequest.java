package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.AdjustmentScope;

import java.util.UUID;

public record AdjustmentCreateRequest(
        AdjustmentScope scope,
        String referenceType,
        String referenceId,
        Long amount,
        String reason,
        String evidenceReference,
        UUID requestedBy
) {
}