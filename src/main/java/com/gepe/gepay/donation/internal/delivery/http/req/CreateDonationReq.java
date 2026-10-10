package com.gepe.gepay.donation.internal.delivery.http.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body pembuatan donasi (publik). Validasi elementer via bean validation;
 * validasi kombinasi field (TEXT/YOUTUBE, URL) ada di service.
 */
public record CreateDonationReq(
        @NotBlank String idempotencyKey,
        @NotNull UUID creatorId,
        @NotNull @Min(1) Long amount,
        String donorName,
        @Email String donorEmail,
        @NotBlank String channelCode,
        @NotBlank String type,
        String message,
        String videoUrl,
        boolean isAnonymous
) {
}
