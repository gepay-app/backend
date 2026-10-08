package com.gepe.gepay.payment.api.dtos;

import java.util.Map;
import java.util.UUID;

/**
 * @param type           kode produk dari modul consumer (mis. "DONATION"); payment
 *                       tidak menafsirkan artinya — hanya menyimpan sebagai label
 * @param channelId      id {@code payment.channels}
 * @param channelRouteId id {@code payment.channel_routes}
 */
public record PaymentCreateRequest(
        String idempotencyKey,
        String type,
        UUID userId,
        UUID payerId,
        Long channelId,
        Long channelRouteId,
        Long grossAmount,
        Map<String, Object> metadata
) {
}