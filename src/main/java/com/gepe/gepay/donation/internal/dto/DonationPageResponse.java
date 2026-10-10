package com.gepe.gepay.donation.internal.dto;

import java.time.Instant;
import java.util.UUID;

/** View halaman donasi milik owner (mengandung {@code overlayKey} rahasia). */
public record DonationPageResponse(
        UUID id,
        UUID creatorId,
        String overlayKey,
        String displayName,
        String title,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
