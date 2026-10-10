package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.PaymentResponse;
import com.gepe.gepay.payment.api.enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/** View pembayaran (HTTP response). */
public record PaymentRes(
        UUID id,
        String idempotencyKey,
        String type,
        PaymentStatus status,
        UUID userId,
        UUID payerId,
        Long providerId,
        Long channelId,
        Long channelRouteId,
        Long grossAmount,
        Long pgFeeAmount,
        Long platformFeeAmount,
        Long totalChargedAmount,
        Long netCreatorAmount,
        Instant createdAt,
        Instant paidAt
) {

    public static PaymentRes from(PaymentResponse p) {
        return new PaymentRes(
                p.id(),
                p.idempotencyKey(),
                p.type(),
                p.status(),
                p.userId(),
                p.payerId(),
                p.providerId(),
                p.channelId(),
                p.channelRouteId(),
                p.grossAmount(),
                p.pgFeeAmount(),
                p.platformFeeAmount(),
                p.totalChargedAmount(),
                p.netCreatorAmount(),
                p.createdAt(),
                p.paidAt());
    }
}
