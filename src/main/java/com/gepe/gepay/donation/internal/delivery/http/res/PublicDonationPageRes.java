package com.gepe.gepay.donation.internal.delivery.http.res;

import com.gepe.gepay.donation.internal.dto.PublicDonationPageResponse;

import java.time.Instant;
import java.util.UUID;

/** View halaman donasi publik — TANPA {@code overlayKey}. Diakses tanpa autentikasi. */
public record PublicDonationPageRes(
        UUID id,
        UUID creatorId,
        String slug,
        String displayName,
        String title,
        String description,
        String imageUrl,
        boolean active,
        Instant createdAt
) {

    public static PublicDonationPageRes from(PublicDonationPageResponse p) {
        return new PublicDonationPageRes(
                p.id(),
                p.creatorId(),
                p.slug(),
                p.displayName(),
                p.title(),
                p.description(),
                p.imageUrl(),
                p.active(),
                p.createdAt());
    }
}
