package com.gepe.gepay.donation.internal.dto;

import java.util.UUID;

/** Hasil penanganan donasi yang lunas: untuk dispatch overlay + notifikasi SSE. */
public record DonationPaidResult(UUID donationId, UUID creatorId) {
}
