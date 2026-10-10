package com.gepe.gepay.donation.internal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Siklus hidup playback display: connect/heartbeat/disconnect + ack event.
 * Dipanggil dari WebSocket handler display (tanpa login — creator sudah
 * di-resolve dari {@code overlay_key} saat handshake).
 */
@Service
@RequiredArgsConstructor
public class OverlayPlaybackService {

    private final OverlayWriter overlayWriter;
    private final OverlayStateStore stateStore;
    private final OverlayDispatcher dispatcher;

    /**
     * Display baru connect: tandai online, coba dispatch backlog FIFO, dan
     * kembalikan data untuk pesan {@code hello}.
     */
    public Map<String, Object> connected(UUID creatorId) {
        stateStore.touchDisplay(creatorId);
        dispatcher.dispatch(creatorId);
        Map<String, Object> hello = new LinkedHashMap<>();
        hello.put("creatorId", creatorId.toString());
        hello.put("queueDepth", overlayWriter.pendingCount(creatorId));
        hello.put("paused", stateStore.isPaused(creatorId));
        return hello;
    }

    public void heartbeat(UUID creatorId) {
        stateStore.touchDisplay(creatorId);
    }

    public void disconnected(UUID creatorId) {
        stateStore.clearDisplay(creatorId);
    }

    /** Display meng-ack event selesai: tandai PLAYED lalu maju ke berikutnya. */
    public void ack(UUID creatorId, UUID overlayEventId) {
        overlayWriter.markPlayedIfCurrent(creatorId, overlayEventId);
        stateStore.clearCurrent(creatorId);
        dispatcher.dispatch(creatorId);
    }
}
