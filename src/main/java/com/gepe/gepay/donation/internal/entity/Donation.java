package com.gepe.gepay.donation.internal.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Satu donasi. Dibuat {@code PENDING} saat donor submit; berubah {@code PAID}
 * lewat {@code PaymentPaidEvent} (lihat fase overlay). {@code videoId} = id
 * YouTube (11 karakter) bila {@code type = YOUTUBE}.
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "donations", schema = "donation")
public class Donation {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "page_id", nullable = false, updatable = false)
    private UUID pageId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 160)
    private String idempotencyKey;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "channel_code", nullable = false, length = 40)
    private String channelCode;

    @Column(name = "payment_reference_number", length = 255)
    private String paymentReferenceNumber;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "total_charged_amount")
    private Long totalChargedAmount;

    @Column(name = "donor_name", length = 120)
    private String donorName;

    @Column(name = "donor_email", length = 200)
    private String donorEmail;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DonationType type;

    @Column(length = 350)
    private String message;

    @Column(name = "video_id", length = 20)
    private String videoId;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DonationStatus status;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Donation create(
            UUID pageId,
            UUID creatorId,
            String idempotencyKey,
            String channelCode,
            String donorName,
            String donorEmail,
            long amount,
            DonationType type,
            String message,
            String videoId,
            boolean isAnonymous
    ) {
        Donation donation = new Donation();
        donation.id = UuidCreator.getTimeOrderedEpoch();
        donation.pageId = pageId;
        donation.creatorId = creatorId;
        donation.idempotencyKey = idempotencyKey;
        donation.channelCode = channelCode;
        donation.donorName = normalize(donorName);
        donation.donorEmail = normalize(donorEmail);
        donation.amount = amount;
        donation.type = type;
        donation.message = normalize(message);
        donation.videoId = videoId;
        donation.isAnonymous = isAnonymous;
        donation.status = DonationStatus.PENDING;
        donation.createdAt = Instant.now();
        donation.updatedAt = donation.createdAt;
        return donation;
    }

    /** Simpan jejak hasil charge payment (payment_id, instruksi bayar, total tagihan). */
    public void attachPayment(UUID paymentId, String referenceNumber, Instant expiresAt, Long totalChargedAmount) {
        this.paymentId = paymentId;
        this.paymentReferenceNumber = referenceNumber;
        this.paymentExpiresAt = expiresAt;
        this.totalChargedAmount = totalChargedAmount;
        this.updatedAt = Instant.now();
    }

    public void markPaid(Instant paidAt, int durationSeconds) {
        this.status = DonationStatus.PAID;
        this.paidAt = paidAt;
        this.durationSeconds = durationSeconds;
        this.updatedAt = Instant.now();
    }

    public void markExpired() {
        this.status = DonationStatus.EXPIRED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = DonationStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
