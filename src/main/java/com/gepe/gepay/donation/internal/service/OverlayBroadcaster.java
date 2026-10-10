package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.OverlayEvent;

import java.util.UUID;

/**
 * Mengirim perintah "play" ke display overlay. Implementasi produksi mem-fan-out
 * lewat Redis pub/sub supaya node yang memegang socket display meneruskannya
 * (multi-instance); pemetaan ke socket ada di Fase 3.
 */
public interface OverlayBroadcaster {

    void play(UUID creatorId, OverlayEvent event);

    /**
     * Beri tahu node lain bahwa {@code overlay_key} creator berubah: sesi display
     * lama ditutup, control menerima key baru.
     */
    void keyRotated(UUID creatorId, String overlayKey);
}
