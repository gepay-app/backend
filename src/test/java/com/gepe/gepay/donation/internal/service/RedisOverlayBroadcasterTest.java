package com.gepe.gepay.donation.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisOverlayBroadcasterTest {

    @Mock
    private StringRedisTemplate redis;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void skipPublishesControlEnvelopeWithEventId() {
        RedisOverlayBroadcaster broadcaster = new RedisOverlayBroadcaster(redis, objectMapper);
        UUID creatorId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        broadcaster.skip(creatorId, eventId);

        ArgumentCaptor<String> channel = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(channel.capture(), payload.capture());
        assertThat(channel.getValue())
                .isEqualTo("donation:overlay:control:" + creatorId);
        assertThat(payload.getValue())
                .contains("\"type\":\"skip\"")
                .contains("\"id\":\"" + eventId + "\"")
                .contains("\"overlayEventId\":\"" + eventId + "\"");
    }

    @Test
    void skipWithoutCurrentPublishesNullId() {
        RedisOverlayBroadcaster broadcaster = new RedisOverlayBroadcaster(redis, objectMapper);
        UUID creatorId = UUID.randomUUID();

        broadcaster.skip(creatorId, null);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(anyString(), payload.capture());
        assertThat(payload.getValue())
                .contains("\"type\":\"skip\"")
                .contains("\"id\":null")
                .contains("\"overlayEventId\":null");
    }

    @Test
    void pausedPublishesPausedAndResumedEnvelopes() {
        RedisOverlayBroadcaster broadcaster = new RedisOverlayBroadcaster(redis, objectMapper);
        UUID creatorId = UUID.randomUUID();

        broadcaster.paused(creatorId, true);
        broadcaster.paused(creatorId, false);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).convertAndSend(anyString(), payload.capture());
        assertThat(payload.getAllValues().get(0))
                .contains("\"type\":\"paused\"")
                .contains("\"paused\":true");
        assertThat(payload.getAllValues().get(1))
                .contains("\"type\":\"resumed\"")
                .contains("\"paused\":false");
    }
}
