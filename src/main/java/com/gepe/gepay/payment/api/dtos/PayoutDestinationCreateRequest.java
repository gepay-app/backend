package com.gepe.gepay.payment.api.dtos;

import java.util.UUID;

/**
 * @param channelId id {@code payment.channels} — channel payout (mis. BANK_BCA)
 */
public record PayoutDestinationCreateRequest(
        UUID userId,
        Long channelId,
        String accountNumber,
        String accountName,
        String bankCode
) {
}