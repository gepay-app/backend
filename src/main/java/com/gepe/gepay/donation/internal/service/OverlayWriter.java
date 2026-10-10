package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.entity.OverlayEvent;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.OverlayEventRepository;
import com.gepe.gepay.donation.internal.util.OverlayPayloadFactory;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Batas transaksi semua mutasi antrean overlay. */
@Service
@RequiredArgsConstructor
public class OverlayWriter {

    private final OverlayEventRepository repository;

    @Transactional(readOnly = true)
    public boolean hasPlaying(UUID creatorId) {
        return repository.existsByCreatorIdAndStatus(creatorId, OverlayEventStatus.PLAYING);
    }

    @Transactional(readOnly = true)
    public long pendingCount(UUID creatorId) {
        return repository.countByCreatorIdAndStatus(creatorId, OverlayEventStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public Optional<OverlayEvent> currentPlaying(UUID creatorId) {
        return repository.findFirstByCreatorIdAndStatusOrderByCreatedAtAsc(creatorId, OverlayEventStatus.PLAYING);
    }

    /**
     * Klaim item {@code PENDING} tertua dan tandai {@code PLAYING}. Lock baris
     * dipegang sampai transaksi commit (dipanggil dari orkestrator yang tidak
     * menyimpan transaksi lebih lama dari ini).
     */
    @Transactional
    public Optional<OverlayEvent> claimNextPending(UUID creatorId) {
        Optional<UUID> id = repository.lockNextPendingId(creatorId);
        if (id.isEmpty()) {
            return Optional.empty();
        }
        OverlayEvent event = repository.findById(id.get())
                .orElseThrow(() -> new ServiceException(DonationError.OVERLAY_EVENT_NOT_FOUND, id.get()));
        event.markPlaying(Instant.now());
        return Optional.of(event);
    }

    /** Buat overlay event untuk donasi bila belum ada (idempotent per donation_id). */
    @Transactional
    public void createIfAbsent(Donation donation, int durationSeconds) {
        if (repository.findByDonationId(donation.getId()).isPresent()) {
            return;
        }
        OverlayEvent event = OverlayEvent.create(
                donation.getId(), donation.getCreatorId(), donation.getType(),
                new LinkedHashMap<>(), durationSeconds);
        event.applyPayload(OverlayPayloadFactory.build(event, donation));
        repository.save(event);
    }

    @Transactional
    public void markPlayed(UUID overlayEventId) {
        find(overlayEventId).markPlayed(Instant.now());
    }

    /**
     * Tandai {@code PLAYED} hanya bila event memang milik creator dan sedang
     * {@code PLAYING} (ack display). Mengembalikan {@code false} bila bukan.
     */
    @Transactional
    public boolean markPlayedIfCurrent(UUID creatorId, UUID overlayEventId) {
        OverlayEvent event = repository.findById(overlayEventId).orElse(null);
        if (event == null
                || !event.getCreatorId().equals(creatorId)
                || event.getStatus() != OverlayEventStatus.PLAYING) {
            return false;
        }
        event.markPlayed(Instant.now());
        return true;
    }

    @Transactional
    public void markSkipped(UUID overlayEventId) {
        find(overlayEventId).markSkipped();
    }

    @Transactional
    public void requeue(UUID overlayEventId) {
        find(overlayEventId).requeue();
    }

    @Transactional
    public int requeueAll(UUID creatorId, OverlayEventStatus fromStatus) {
        List<OverlayEvent> events =
                repository.findByCreatorIdAndStatusOrderByCreatedAtAsc(creatorId, fromStatus);
        events.forEach(OverlayEvent::requeue);
        return events.size();
    }

    @Transactional
    public int purgePending(UUID creatorId) {
        return (int) repository.deleteByCreatorIdAndStatus(creatorId, OverlayEventStatus.PENDING);
    }

    /**
     * Watchdog: event {@code PLAYING} yang lewat timeout ack ditandai ulang.
     * Mengembalikan daftar creator yang perlu di-dispatch lagi.
     */
    @Transactional
    public List<UUID> recoverTimedOut(int ackTimeoutSeconds, boolean requeue) {
        Instant cutoff = Instant.now().minusSeconds(ackTimeoutSeconds);
        List<OverlayEvent> stuck =
                repository.findByStatusAndDispatchedAtBefore(OverlayEventStatus.PLAYING, cutoff);
        Set<UUID> creators = new LinkedHashSet<>();
        for (OverlayEvent event : stuck) {
            if (requeue) {
                event.requeue();
            } else {
                event.markFailed();
            }
            creators.add(event.getCreatorId());
        }
        return List.copyOf(creators);
    }

    private OverlayEvent find(UUID overlayEventId) {
        return repository.findById(overlayEventId)
                .orElseThrow(() -> new ServiceException(DonationError.OVERLAY_EVENT_NOT_FOUND, overlayEventId));
    }
}
