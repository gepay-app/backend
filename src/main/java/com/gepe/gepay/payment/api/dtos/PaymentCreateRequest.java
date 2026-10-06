package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.PaymentType;

import java.util.Map;
import java.util.UUID;

/**
 * @param channelId      id {@code payment.channels}
 * @param channelRouteId id {@code payment.channel_routes}
 */
public record PaymentCreateRequest(
        String idempotencyKey,
        PaymentType type,
        UUID userId,
        UUID payerId,
        Long channelId,
        Long channelRouteId,
        Long grossAmount,
        Map<String, Object> metadata
) {
}