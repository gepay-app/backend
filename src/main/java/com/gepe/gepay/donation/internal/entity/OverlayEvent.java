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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Satu item antrean overlay (durable di DB). Dibuat saat donation {@code PAID}.
 * {@code payload} = data siap kirim ke display OBS (lihat {@code todo.md} §7.2).
 */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "overlay_events", schema = "donation")
public class OverlayEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "donation_id", nullable = false, unique = true, updatable = false)
    private UUID donationId;

    @Column(name = "creator_id", nullable = false, updatable = false)
    private UUID creatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private DonationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OverlayEventStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "played_at")
    private Instant playedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OverlayEvent create(
            UUID donationId,
            UUID creatorId,
            DonationType type,
            Map<String, Object> payload,
            int durationSeconds
    ) {
        OverlayEvent event = new OverlayEvent();
        event.id = UuidCreator.getTimeOrderedEpoch();
        event.donationId = donationId;
        event.creatorId = creatorId;
        event.type = type;
        event.status = OverlayEventStatus.PENDING;
        event.payload = payload;
        event.durationSeconds = durationSeconds;
        event.attempts = 0;
        event.createdAt = Instant.now();
        event.updatedAt = event.createdAt;
        return event;
    }

    /** Isi payload setelah id ter-generate (dipakai saat membuat event). */
    public void applyPayload(Map<String, Object> payload) {
        this.payload = payload;
        this.updatedAt = Instant.now();
    }

    public void markPlaying(Instant dispatchedAt) {
        this.status = OverlayEventStatus.PLAYING;
        this.dispatchedAt = dispatchedAt;
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    public void markPlayed(Instant playedAt) {
        this.status = OverlayEventStatus.PLAYED;
        this.playedAt = playedAt;
        this.updatedAt = Instant.now();
    }

    public void markSkipped() {
        this.status = OverlayEventStatus.SKIPPED;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = OverlayEventStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void requeue() {
        this.status = OverlayEventStatus.PENDING;
        this.dispatchedAt = null;
        this.playedAt = null;
        this.updatedAt = Instant.now();
    }
}
