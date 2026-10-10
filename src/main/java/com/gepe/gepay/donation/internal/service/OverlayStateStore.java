package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.config.DonationOverlayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * State transient overlay di Redis (shared lintas instance): presence display,
 * flag paused, dan event yang sedang {@code PLAYING}. Kebenaran durable tetap di
 * DB; state ini hanya untuk memutuskan dispatch & routing.
 */
@Component
@RequiredArgsConstructor
public class OverlayStateStore {

    private static final String PRESENCE_PREFIX = "donation:overlay:presence:";
    private static final String PAUSED_PREFIX = "donation:overlay:paused:";
    private static final String CURRENT_PREFIX = "donation:overlay:current:";

    private final StringRedisTemplate redis;
    private final DonationOverlayProperties properties;

    /** Heartbeat display; presence kedaluwarsa bila tidak di-refresh. */
    public void touchDisplay(UUID creatorId) {
        redis.opsForValue().set(PRESENCE_PREFIX + creatorId, "1",
                Duration.ofSeconds(properties.getPresenceTtlSeconds()));
    }

    public void clearDisplay(UUID creatorId) {
        redis.delete(PRESENCE_PREFIX + creatorId);
        redis.delete(CURRENT_PREFIX + creatorId);
    }

    public boolean isDisplayOnline(UUID creatorId) {
        return Boolean.TRUE.equals(redis.hasKey(PRESENCE_PREFIX + creatorId));
    }

    public void setPaused(UUID creatorId, boolean paused) {
        if (paused) {
            redis.opsForValue().set(PAUSED_PREFIX + creatorId, "1");
        } else {
            redis.delete(PAUSED_PREFIX + creatorId);
        }
    }

    public boolean isPaused(UUID creatorId) {
        return Boolean.TRUE.equals(redis.hasKey(PAUSED_PREFIX + creatorId));
    }

    public void setCurrent(UUID creatorId, UUID overlayEventId) {
        redis.opsForValue().set(CURRENT_PREFIX + creatorId, overlayEventId.toString());
    }

    public void clearCurrent(UUID creatorId) {
        redis.delete(CURRENT_PREFIX + creatorId);
    }

    public Optional<UUID> getCurrent(UUID creatorId) {
        String value = redis.opsForValue().get(CURRENT_PREFIX + creatorId);
        return value == null ? Optional.empty() : Optional.of(UUID.fromString(value));
    }
}
