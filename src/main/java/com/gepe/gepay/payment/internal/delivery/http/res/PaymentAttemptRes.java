package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.PaymentAttemptResponse;
import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;

import java.time.Instant;
import java.util.UUID;

/** View satu percobaan bayar (HTTP response). */
public record PaymentAttemptRes(
        UUID id,
        UUID paymentId,
        Long channelRouteId,
        String providerReferenceId,
        String paymentReferenceNumber,
        PaymentAttemptStatus status,
        Instant expiresAt,
        Instant createdAt
) {

    public static PaymentAttemptRes from(PaymentAttemptResponse a) {
        return new PaymentAttemptRes(
                a.id(),
                a.paymentId(),
                a.channelRouteId(),
                a.providerReferenceId(),
                a.paymentReferenceNumber(),
                a.status(),
                a.expiresAt(),
                a.createdAt());
    }
}
