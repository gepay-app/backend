package com.gepe.gepay.donation.internal.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Helper protokol JSON sederhana untuk WebSocket overlay (lihat {@code todo.md} §7.3). */
@Slf4j
public final class OverlayWsSupport {

    public static final String ATTR_CREATOR_ID = "creatorId";
    public static final String ATTR_ROLE = "role";
    public static final String ROLE_DISPLAY = "display";
    public static final String ROLE_CONTROL = "control";

    private OverlayWsSupport() {
    }

    public static UUID creatorId(WebSocketSession session) {
        return (UUID) session.getAttributes().get(ATTR_CREATOR_ID);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(ObjectMapper objectMapper, String payload) {
        try {
            return objectMapper.readValue(payload, Map.class);
        } catch (RuntimeException e) {
            log.debug("Ignoring malformed overlay WS message: {}", payload);
            return Map.of();
        }
    }

    public static Map<String, Object> envelope(String type, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("v", 1);
        envelope.put("type", type);
        envelope.put("data", data);
        return envelope;
    }

    /** Kirim aman (satu sesi tidak thread-safe): sinkronkan + cek terbuka. */
    public static void send(ObjectMapper objectMapper, WebSocketSession session, Object envelope) {
        if (!session.isOpen()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(envelope);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to send overlay WS message to session {}", session.getId(), e);
        }
    }
}
