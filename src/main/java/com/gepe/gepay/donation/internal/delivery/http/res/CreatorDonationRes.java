package com.gepe.gepay.donation.internal.delivery.http.res;

import com.gepe.gepay.donation.internal.dto.CreatorDonationResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * View riwayat donasi milik creator (dashboard, butuh autentikasi). Creator boleh
 * melihat identitas donor (termasuk saat anonim); UI memakai {@code isAnonymous}
 * untuk menyembunyikannya bila perlu.
 */
public record CreatorDonationRes(
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

    public static CreatorDonationRes from(CreatorDonationResponse d) {
        return new CreatorDonationRes(
                d.donationId(),
                d.status(),
                d.type(),
                d.amount(),
                d.totalChargedAmount(),
                d.donorName(),
                d.donorEmail(),
                d.isAnonymous(),
                d.message(),
                d.videoId(),
                d.channelCode(),
                d.createdAt(),
                d.paidAt());
    }
}
