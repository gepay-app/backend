package com.gepe.gepay.donation.internal.dto;

/** Command internal update profil halaman donasi. */
public record UpdateDonationPageCommand(
        String displayName,
        String title,
        String description
) {
}
