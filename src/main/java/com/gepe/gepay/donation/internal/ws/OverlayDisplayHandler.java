package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.service.OverlayPlaybackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Display overlay (OBS) — <strong>pasif, tanpa login</strong>. Creator sudah
 * di-resolve dari {@code overlay_key} saat handshake. Hanya menerima perintah
 * {@code play} dan membalas {@code ack}/{@code heartbeat}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverlayDisplayHandler extends TextWebSocketHandler {

    private final OverlaySessionRegistry registry;
    private final OverlayPlaybackService playback;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID creatorId = OverlayWsSupport.creatorId(session);
        registry.addDisplay(creatorId, session);
        Map<String, Object> hello = playback.connected(creatorId);
        OverlayWsSupport.send(objectMapper, session, OverlayWsSupport.envelope("hello", hello));
        log.debug("Overlay display connected: creator={}, session={}", creatorId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID creatorId = OverlayWsSupport.creatorId(session);
        Map<String, Object> payload = OverlayWsSupport.parse(objectMapper, message.getPayload());
        String type = asString(payload.get("type"));
        if ("ack".equals(type)) {
            String id = asString(payload.get("id"));
            if (id != null) {
                playback.ack(creatorId, UUID.fromString(id));
            }
        } else if ("heartbeat".equals(type)) {
            playback.heartbeat(creatorId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID creatorId = OverlayWsSupport.creatorId(session);
        registry.removeDisplay(creatorId, session);
        if (!registry.hasDisplay(creatorId)) {
            playback.disconnected(creatorId);
        }
        log.debug("Overlay display disconnected: creator={}, session={}", creatorId, session.getId());
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
