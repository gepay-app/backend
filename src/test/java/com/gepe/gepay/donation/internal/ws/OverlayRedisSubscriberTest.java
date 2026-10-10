package com.gepe.gepay.donation.internal.ws;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverlayRedisSubscriberTest {

    @Mock
    private OverlaySessionRegistry registry;
    @Mock
    private WebSocketSession session;

    @Test
    void forwardsPlayMessageToLocalSessions() throws Exception {
        UUID creatorId = UUID.randomUUID();
        byte[] channel = ("donation:overlay:play:" + creatorId).getBytes(StandardCharsets.UTF_8);
        byte[] body = "{\"type\":\"play\"}".getBytes(StandardCharsets.UTF_8);
        when(registry.displaySessions(creatorId)).thenReturn(Set.of(session));
        when(session.isOpen()).thenReturn(true);

        new OverlayRedisSubscriber(registry).onMessage(new DefaultMessage(channel, body), null);

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void ignoresMessagesForUnparsableChannel() {
        byte[] channel = "donation:overlay:play:not-a-uuid".getBytes(StandardCharsets.UTF_8);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        new OverlayRedisSubscriber(registry).onMessage(new DefaultMessage(channel, body), null);

        verifyNoInteractions(registry);
    }
}
