package com.gepe.gepay.donation.internal.dto;

import java.time.Instant;
import java.util.UUID;

/** View donasi (dipakai halaman pembayaran & konfirmasi). */
public record DonationResponse(
        UUID donationId,
        UUID paymentId,
        UUID creatorId,
        String status,
        String type,
        long amount,
        Long totalChargedAmount,
        String channelCode,
        String message,
        String videoId,
        String paymentReferenceNumber,
        Instant paymentExpiresAt,
        Instant createdAt,
        Instant paidAt
) {
}
