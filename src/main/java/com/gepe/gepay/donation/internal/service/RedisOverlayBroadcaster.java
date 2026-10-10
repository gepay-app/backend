package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.OverlayEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fan-out perintah play lewat Redis pub/sub channel
 * {@code donation:overlay:play:{creatorId}}. Setiap instance subscribe; yang
 * memegang socket display creator tsb meneruskan ke client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOverlayBroadcaster implements OverlayBroadcaster {

    public static final String PLAY_CHANNEL_PREFIX = "donation:overlay:play:";
    public static final String KEY_ROTATED_CHANNEL_PREFIX = "donation:overlay:key-rotated:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    public void play(UUID creatorId, OverlayEvent event) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", 1);
        envelope.put("type", "play");
        envelope.put("id", event.getId().toString());
        envelope.put("data", event.getPayload());

        String message = objectMapper.writeValueAsString(envelope);
        redis.convertAndSend(PLAY_CHANNEL_PREFIX + creatorId, message);
        log.debug("Published overlay play for creator={}, event={}", creatorId, event.getId());
    }

    @Override
    public void keyRotated(UUID creatorId, String overlayKey) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", 1);
        envelope.put("type", "keyRotated");
        envelope.put("data", Map.of("overlayKey", overlayKey));

        redis.convertAndSend(KEY_ROTATED_CHANNEL_PREFIX + creatorId,
                objectMapper.writeValueAsString(envelope));
        log.debug("Published overlay key rotation for creator={}", creatorId);
    }
}
