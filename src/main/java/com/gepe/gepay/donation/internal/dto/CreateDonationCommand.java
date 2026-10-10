package com.gepe.gepay.donation.internal.dto;

import java.util.UUID;

/**
 * Command internal pembuatan donasi. {@code videoUrl} masih mentah (validasi &
 * parse ada di service).
 */
public record CreateDonationCommand(
        String idempotencyKey,
        UUID creatorId,
        long amount,
        String donorName,
        String donorEmail,
        String channelCode,
        String type,
        String message,
        String videoUrl,
        boolean isAnonymous
) {
}
