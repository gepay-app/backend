package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.FundTransferDirection;
import com.gepe.gepay.payment.api.enums.FundTransferSourceType;
import com.gepe.gepay.payment.api.enums.FundTransferTargetType;

import java.util.UUID;

public record FundTransferCreateRequest(
        FundTransferDirection direction,
        FundTransferSourceType sourceType,
        FundTransferTargetType targetType,
        Long sentAmount,
        Long feeAmount,
        String note,
        UUID createdBy
) {
}