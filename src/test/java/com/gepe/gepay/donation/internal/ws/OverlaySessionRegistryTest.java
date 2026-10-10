package com.gepe.gepay.donation.internal.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OverlaySessionRegistryTest {

    private final OverlaySessionRegistry registry = new OverlaySessionRegistry();

    @Test
    void tracksDisplaySessionsPerCreator() {
        UUID creatorId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);

        registry.addDisplay(creatorId, session);
        assertThat(registry.hasDisplay(creatorId)).isTrue();
        assertThat(registry.displaySessions(creatorId)).containsExactly(session);

        registry.removeDisplay(creatorId, session);
        assertThat(registry.hasDisplay(creatorId)).isFalse();
        assertThat(registry.displaySessions(creatorId)).isEmpty();
    }

    @Test
    void tracksControlSessionsPerCreator() {
        UUID creatorId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);

        registry.addControl(creatorId, session);
        assertThat(registry.controlSessions(creatorId)).containsExactly(session);

        registry.removeControl(creatorId, session);
        assertThat(registry.controlSessions(creatorId)).isEmpty();
    }
}
