package com.gepe.gepay.donation.internal.delivery.http.res;

import com.gepe.gepay.donation.internal.dto.DonationResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * View donasi untuk halaman publik (pembayaran &amp; konfirmasi donor). Hanya
 * memuat field yang boleh dilihat tanpa autentikasi — {@code paymentId} internal
 * dan rincian fee tidak diteruskan. Jangan tambahkan data sensitif ke record ini.
 */
public record DonationRes(
        UUID donationId,
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

    public static DonationRes from(DonationResponse d) {
        return new DonationRes(
                d.donationId(),
                d.creatorId(),
                d.status(),
                d.type(),
                d.amount(),
                d.totalChargedAmount(),
                d.channelCode(),
                d.message(),
                d.videoId(),
                d.paymentReferenceNumber(),
                d.paymentExpiresAt(),
                d.createdAt(),
                d.paidAt());
    }
}
