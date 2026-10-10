package com.gepe.gepay.payment.api.dtos;

import java.util.UUID;

/**
 * Perintah buat payout destination (rekening tujuan pencairan) milik creator.
 * {@code channelId} harus menunjuk channel ber-direction {@code PAYOUT}.
 */
public record PayoutDestinationCreateCommand(
        Long channelId,
        String accountNumber,
        String accountName,
        String bankCode
) {
}
