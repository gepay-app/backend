package com.gepe.gepay.donation.internal.dto;

import java.time.Instant;
import java.util.UUID;

/** View halaman donasi publik — TANPA {@code overlayKey}. */
public record PublicDonationPageResponse(
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
}
