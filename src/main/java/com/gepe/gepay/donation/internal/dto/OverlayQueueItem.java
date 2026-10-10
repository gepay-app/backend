package com.gepe.gepay.donation.internal.dto;

import java.time.Instant;
import java.util.UUID;

/** Satu item antrean overlay untuk control page / owner. */
public record OverlayQueueItem(
        UUID overlayEventId,
        UUID donationId,
        String type,
        String status,
        long amount,
        String donorName,
        int durationSeconds,
        int attempts,
        Instant createdAt,
        Instant playedAt
) {
}
