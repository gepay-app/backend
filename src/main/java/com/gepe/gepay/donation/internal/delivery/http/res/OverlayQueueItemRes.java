package com.gepe.gepay.donation.internal.delivery.http.res;

import com.gepe.gepay.donation.internal.dto.OverlayQueueItem;

import java.time.Instant;
import java.util.UUID;

/** Satu item antrean overlay untuk control page (owner, butuh autentikasi). */
public record OverlayQueueItemRes(
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

    public static OverlayQueueItemRes from(OverlayQueueItem i) {
        return new OverlayQueueItemRes(
                i.overlayEventId(),
                i.donationId(),
                i.type(),
                i.status(),
                i.amount(),
                i.donorName(),
                i.durationSeconds(),
                i.attempts(),
                i.createdAt(),
                i.playedAt());
    }
}
