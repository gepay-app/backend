package com.gepe.gepay.donation.internal.repository;

import com.gepe.gepay.donation.internal.entity.OverlayEvent;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OverlayEventRepository extends JpaRepository<OverlayEvent, UUID> {

    Optional<OverlayEvent> findByDonationId(UUID donationId);

    boolean existsByCreatorIdAndStatus(UUID creatorId, OverlayEventStatus status);

    long countByCreatorIdAndStatus(UUID creatorId, OverlayEventStatus status);

    Optional<OverlayEvent> findFirstByCreatorIdAndStatusOrderByCreatedAtAsc(UUID creatorId, OverlayEventStatus status);

    List<OverlayEvent> findByCreatorIdAndStatusOrderByCreatedAtAsc(UUID creatorId, OverlayEventStatus status);

    List<OverlayEvent> findByCreatorIdOrderByCreatedAtAsc(UUID creatorId);

    List<OverlayEvent> findByStatusAndDispatchedAtBefore(OverlayEventStatus status, Instant cutoff);

    long deleteByCreatorIdAndStatus(UUID creatorId, OverlayEventStatus status);

    /**
     * Klaim item {@code PENDING} tertua milik creator secara atomik lintas
     * instance: baris dikunci sampai transaksi selesai, item yang sedang
     * dikunci instance lain di-skip (bukan diblokir).
     */
    @Query(value = """
            SELECT id FROM donation.overlay_events
            WHERE creator_id = :creatorId AND status = 'PENDING'
            ORDER BY created_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<UUID> lockNextPendingId(@Param("creatorId") UUID creatorId);
}
