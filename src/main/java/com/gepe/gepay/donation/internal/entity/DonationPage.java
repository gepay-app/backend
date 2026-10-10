package com.gepe.gepay.donation.internal.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Halaman donasi milik satu creator (1:1). {@code creatorId} = userId penerima;
 * {@code overlayKey} = rahasia untuk display OBS dan bisa dirotasi.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "donation_pages", schema = "donation")
public class DonationPage {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false, unique = true, updatable = false)
    private UUID creatorId;

    @Column(name = "overlay_key", nullable = false, unique = true, length = 64)
    private String overlayKey;

    @Column(name = "display_name", length = 120)
    private String displayName;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static DonationPage create(UUID creatorId, String overlayKey) {
        DonationPage page = new DonationPage();
        page.id = UuidCreator.getTimeOrderedEpoch();
        page.creatorId = creatorId;
        page.overlayKey = overlayKey;
        page.isActive = true;
        page.createdAt = Instant.now();
        page.updatedAt = page.createdAt;
        return page;
    }

    public void updateProfile(String displayName, String title, String description) {
        this.displayName = normalize(displayName);
        this.title = normalize(title);
        this.description = normalize(description);
        this.updatedAt = Instant.now();
    }

    public void rotateOverlayKey(String newOverlayKey) {
        this.overlayKey = newOverlayKey;
        this.updatedAt = Instant.now();
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
