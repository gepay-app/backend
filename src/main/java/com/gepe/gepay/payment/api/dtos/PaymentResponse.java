package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
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
}
