package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.WithdrawalStatus;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalResponse(
        UUID id,
        String idempotencyKey,
        UUID userId,
        UUID destinationId,
        Long channelRouteId,
        WithdrawalStatus status,
        Long requestedAmount,
        Long withdrawalFeeAmount,
        Long netDisbursementAmount,
        String destinationAccountNumber,
        String destinationAccountName,
        String destinationBankCode,
        Instant createdAt,
        Instant completedAt
) {
}
