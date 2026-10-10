package com.gepe.gepay.donation.internal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE status pembayaran donasi. Emitter disimpan <strong>lokal node</strong>;
 * sinyal PAID di-fan-out lewat Redis pub/sub ({@code donation:payment:{id}}) agar
 * node mana pun bisa mengirim ke koneksi donor. Boleh putus kapan saja (bukan
 * sumber kebenaran — donor bisa refresh & baca {@code GET /donations/{id}}).
 */
@Service
@RequiredArgsConstructor
public class DonationStatusStreamService {

    public static final String PAID_CHANNEL_PREFIX = "donation:payment:";
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final StringRedisTemplate redis;
    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID donationId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(donationId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable cleanup = () -> remove(donationId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    /** Dikirim oleh node yang memproses event PAID. */
    public void publishPaid(UUID donationId) {
        redis.convertAndSend(PAID_CHANNEL_PREFIX + donationId, "paid");
    }

    /** Dipanggil subscriber Redis / handler lokal untuk koneksi di node ini. */
    public void deliverPaid(UUID donationId) {
        List<SseEmitter> list = emitters.get(donationId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("paid").data("paid"));
            } catch (Exception e) {
                remove(donationId, emitter);
            }
        }
    }

    private void remove(UUID donationId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(donationId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(donationId);
            }
        }
    }
}
