package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.PayoutDestinationResponse;

import java.time.Instant;
import java.util.UUID;

/** View rekening tujuan penarikan (HTTP response). */
public record PayoutDestinationRes(
        UUID id,
        Long channelId,
        String accountNumber,
        String accountName,
        String bankCode,
        Boolean isDefault,
        Boolean isActive,
        Instant createdAt
) {

    public static PayoutDestinationRes from(PayoutDestinationResponse d) {
        return new PayoutDestinationRes(
                d.id(),
                d.channelId(),
                d.accountNumber(),
                d.accountName(),
                d.bankCode(),
                d.isDefault(),
                d.isActive(),
                d.createdAt());
    }
}
