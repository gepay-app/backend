package com.gepe.gepay.donation.internal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Memajukan antrean overlay satu langkah (FIFO, satu per satu). Aman lintas
 * instance: klaim baris atomik di {@link OverlayWriter#claimNextPending}, dan
 * presence/paused dibaca dari Redis bersama.
 *
 * <p>Tidak {@code @Transactional}: klaim punya transaksinya sendiri, broadcast
 * terjadi setelah klaim commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverlayDispatcher {

    private final OverlayWriter overlayWriter;
    private final OverlayStateStore stateStore;
    private final OverlayBroadcaster broadcaster;

    public void dispatch(UUID creatorId) {
        if (stateStore.isPaused(creatorId)) {
            return;
        }
        if (overlayWriter.hasPlaying(creatorId)) {
            return;
        }
        if (!stateStore.isDisplayOnline(creatorId)) {
            return; // display offline — biarkan PENDING (durable)
        }

        overlayWriter.claimNextPending(creatorId).ifPresent(event -> {
            stateStore.setCurrent(creatorId, event.getId());
            broadcaster.play(creatorId, event);
            log.info("Dispatched overlay event {} to creator {}", event.getId(), creatorId);
        });
    }
}
