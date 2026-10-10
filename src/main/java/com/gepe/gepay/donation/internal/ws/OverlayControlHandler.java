package com.gepe.gepay.donation.internal.ws;

import com.gepe.gepay.donation.internal.dto.OverlayQueueItem;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import com.gepe.gepay.donation.internal.service.DonationPageService;
import com.gepe.gepay.donation.internal.service.OverlayQueueService;
import com.gepe.gepay.donation.internal.service.OverlayStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Control overlay (owner) — butuh login (token Firebase divalidasi saat
 * handshake). Menerima command pause/resume/skip/retry/retryAll/purge dan
 * mengirim snapshot {@code state}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverlayControlHandler extends TextWebSocketHandler {

    private final OverlaySessionRegistry registry;
    private final OverlayQueueService queueService;
    private final OverlayStateStore stateStore;
    private final DonationPageService donationPageService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID creatorId = OverlayWsSupport.creatorId(session);
        registry.addControl(creatorId, session);
        sendState(session, creatorId);
        log.debug("Overlay control connected: creator={}, session={}", creatorId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID creatorId = OverlayWsSupport.creatorId(session);
        Map<String, Object> payload = OverlayWsSupport.parse(objectMapper, message.getPayload());
        String type = asString(payload.get("type"));
        try {
            switch (type == null ? "" : type) {
                case "pause" -> queueService.pause(creatorId);
                case "resume" -> queueService.resume(creatorId);
                case "skip" -> queueService.skip(creatorId);
                case "retry" -> queueService.retry(creatorId, UUID.fromString(asString(payload.get("id"))));
                case "retryAll" -> queueService.retryAll(creatorId, parseStatus(payload.get("fromStatus")));
                case "purge" -> queueService.purge(creatorId);
                default -> {
                    OverlayWsSupport.send(objectMapper, session,
                            OverlayWsSupport.envelope("error", Map.of("code", "donation.invalid_overlay_command")));
                    return;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Overlay control command '{}' failed for creator={}: {}", type, creatorId, e.toString());
        }
        sendState(session, creatorId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.removeControl(OverlayWsSupport.creatorId(session), session);
    }

    private void sendState(WebSocketSession session, UUID creatorId) {
        List<OverlayQueueItem> queue = queueService.list(creatorId, null);
        String overlayKey = donationPageService.requireByCreatorId(creatorId).getOverlayKey();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paused", stateStore.isPaused(creatorId));
        data.put("displayOnline", stateStore.isDisplayOnline(creatorId));
        data.put("overlayKey", overlayKey);
        data.put("current", stateStore.getCurrent(creatorId).map(UUID::toString).orElse(null));
        data.put("queue", queue);
        OverlayWsSupport.send(objectMapper, session, OverlayWsSupport.envelope("state", data));
    }

    private static OverlayEventStatus parseStatus(Object value) {
        String raw = value == null ? null : value.toString();
        return raw == null ? OverlayEventStatus.FAILED : OverlayEventStatus.valueOf(raw);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
