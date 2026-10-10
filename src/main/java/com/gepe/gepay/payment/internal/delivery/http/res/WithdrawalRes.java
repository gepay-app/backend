package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.WithdrawalResponse;
import com.gepe.gepay.payment.api.enums.WithdrawalStatus;

import java.time.Instant;
import java.util.UUID;

/** View penarikan (HTTP response). */
public record WithdrawalRes(
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

    public static WithdrawalRes from(WithdrawalResponse w) {
        return new WithdrawalRes(
                w.id(),
                w.idempotencyKey(),
                w.userId(),
                w.destinationId(),
                w.channelRouteId(),
                w.status(),
                w.requestedAmount(),
                w.withdrawalFeeAmount(),
                w.netDisbursementAmount(),
                w.destinationAccountNumber(),
                w.destinationAccountName(),
                w.destinationBankCode(),
                w.createdAt(),
                w.completedAt());
    }
}
