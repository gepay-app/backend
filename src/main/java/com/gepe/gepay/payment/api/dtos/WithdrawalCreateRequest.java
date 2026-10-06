package com.gepe.gepay.payment.api.dtos;

import java.util.UUID;

/**
 * @param destinationId  id {@code payment.payout_destinations}
 * @param channelRouteId id {@code payment.channel_routes}
 */
public record WithdrawalCreateRequest(
        String idempotencyKey,
        UUID userId,
        UUID destinationId,
        Long channelRouteId,
        Long requestedAmount
) {
}