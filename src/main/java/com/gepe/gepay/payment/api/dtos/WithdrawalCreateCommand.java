package com.gepe.gepay.payment.api.dtos;

import java.util.UUID;

/**
 * Perintah tarik dana. Route (provider + channel) diturunkan dari
 * {@code destinationId}, jadi tidak perlu dikirim klien. Idempoten via
 * {@code idempotencyKey}.
 */
public record WithdrawalCreateCommand(
        String idempotencyKey,
        UUID destinationId,
        Long requestedAmount
) {
}
