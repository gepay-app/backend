package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.service.RedisOverlayBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Meneruskan pesan Redis pub/sub ke sesi WebSocket <strong>lokal</strong> milik
 * creator terkait: perintah {@code play} (display + control) dan
 * {@code keyRotated} (menutup display lama, memberi tahu control). Inilah yang
 * membuat fan-out benar saat webhook diproses di node lain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverlayRedisSubscriber implements MessageListener {

    private final OverlaySessionRegistry registry;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        UUID creatorId = parseCreatorId(channel);
        if (creatorId == null) {
            return;
        }
        if (channel.startsWith(RedisOverlayBroadcaster.KEY_ROTATED_CHANNEL_PREFIX)) {
            registry.closeDisplaySessions(creatorId);
        }
        sendToAll(registry.displaySessions(creatorId), body);
        sendToAll(registry.controlSessions(creatorId), body);
    }

    private void sendToAll(Set<WebSocketSession> sessions, String body) {
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(body));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to forward overlay play to session {}", session.getId(), e);
            }
        }
    }

    private static UUID parseCreatorId(String channel) {
        int idx = channel.lastIndexOf(':');
        if (idx < 0) {
            return null;
        }
        try {
            return UUID.fromString(channel.substring(idx + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
