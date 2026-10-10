package com.gepe.gepay.donation.internal.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Satu donasi pada dashboard creator (riwayat + pesan donor). Berbeda dengan
 * {@link DonationResponse} milik donor, di sini tidak ada instruksi bayar lagi.
 * {@code donorName}/{@code donorEmail} selalu diisi; UI memakai
 * {@code isAnonymous} untuk menyembunyikannya bila perlu.
 */
public record CreatorDonationResponse(
        UUID donationId,
        String status,
        String type,
        long amount,
        Long totalChargedAmount,
        String donorName,
        String donorEmail,
        boolean isAnonymous,
        String message,
        String videoId,
        String channelCode,
        Instant createdAt,
        Instant paidAt
) {
}
