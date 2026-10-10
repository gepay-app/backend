package com.gepe.gepay.donation.internal.dto;

/** Command internal update profil halaman donasi. {@code slug} null = tetap. */
public record UpdateDonationPageCommand(
        String displayName,
        String title,
        String description,
        String imageUrl,
        String slug
) {
}
