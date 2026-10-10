package com.gepe.gepay.donation.internal.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry sesi WebSocket <strong>lokal node</strong> per creator. Fan-out
 * lintas node dilakukan lewat Redis pub/sub; node yang memegang socket
 * meneruskan ke sesi lokalnya di sini.
 */
@Component
public class OverlaySessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> displaySessions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<WebSocketSession>> controlSessions = new ConcurrentHashMap<>();

    public void addDisplay(UUID creatorId, WebSocketSession session) {
        displaySessions.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void addControl(UUID creatorId, WebSocketSession session) {
        controlSessions.computeIfAbsent(creatorId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void removeDisplay(UUID creatorId, WebSocketSession session) {
        remove(displaySessions, creatorId, session);
    }

    public void removeControl(UUID creatorId, WebSocketSession session) {
        remove(controlSessions, creatorId, session);
    }

    public boolean hasDisplay(UUID creatorId) {
        Set<WebSocketSession> sessions = displaySessions.get(creatorId);
        return sessions != null && !sessions.isEmpty();
    }

    public Set<WebSocketSession> displaySessions(UUID creatorId) {
        return Set.copyOf(displaySessions.getOrDefault(creatorId, Set.of()));
    }

    public Set<WebSocketSession> controlSessions(UUID creatorId) {
        return Set.copyOf(controlSessions.getOrDefault(creatorId, Set.of()));
    }

    /** Tutup semua sesi display creator (dipakai saat {@code overlay_key} dirotasi). */
    public void closeDisplaySessions(UUID creatorId) {
        Set<WebSocketSession> sessions = displaySessions.remove(creatorId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (Exception ignored) {
                // best effort
            }
        }
    }

    private void remove(Map<UUID, Set<WebSocketSession>> map, UUID creatorId, WebSocketSession session) {
        Set<WebSocketSession> sessions = map.get(creatorId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                map.remove(creatorId);
            }
        }
    }
}
