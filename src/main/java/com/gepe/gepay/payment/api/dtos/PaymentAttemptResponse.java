package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentAttemptResponse(
        UUID id,
        UUID paymentId,
        Long channelRouteId,
        String providerReferenceId,
        String paymentReferenceNumber,
        PaymentAttemptStatus status,
        Instant expiresAt,
        Instant createdAt
) {
}
