package com.gepe.gepay.donation.internal.delivery.http.res;

import com.gepe.gepay.donation.internal.dto.DonationPageResponse;

import java.time.Instant;
import java.util.UUID;

/** View halaman donasi milik owner (termasuk {@code overlayKey} rahasia). Butuh autentikasi. */
public record DonationPageRes(
        UUID id,
        UUID creatorId,
        String overlayKey,
        String slug,
        String displayName,
        String title,
        String description,
        String imageUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static DonationPageRes from(DonationPageResponse p) {
        return new DonationPageRes(
                p.id(),
                p.creatorId(),
                p.overlayKey(),
                p.slug(),
                p.displayName(),
                p.title(),
                p.description(),
                p.imageUrl(),
                p.active(),
                p.createdAt(),
                p.updatedAt());
    }
}
