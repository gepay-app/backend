package com.gepe.gepay.donation.internal.delivery.http;

import com.gepe.gepay.donation.internal.delivery.http.res.OverlayQueueItemRes;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import com.gepe.gepay.donation.internal.service.OverlayQueueService;
import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Kontrol overlay (owner). Endpoint ini butuh login; pengendalian realtime juga
 * tersedia lewat WebSocket {@code /ws/overlay/control}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/donations/overlay")
public class OverlayControlController {

    private final OverlayQueueService queueService;
    private final CurrentUser currentUser;
    private final MessageHelper messageHelper;

    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<OverlayQueueItemRes>>> queue(
            @RequestParam(value = "status", required = false) OverlayEventStatus status) {
        List<OverlayQueueItemRes> items = queueService.list(currentUser.userId(), status)
                .stream().map(OverlayQueueItemRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(messageHelper.get("donation.overlay_queue_retrieved"), items));
    }

    @PostMapping("/pause")
    public ResponseEntity<ApiResponse<Void>> pause() {
        queueService.pause(currentUser.userId());
        return ok("donation.overlay_paused");
    }

    @PostMapping("/resume")
    public ResponseEntity<ApiResponse<Void>> resume() {
        queueService.resume(currentUser.userId());
        return ok("donation.overlay_resumed");
    }

    @PostMapping("/skip")
    public ResponseEntity<ApiResponse<Void>> skip() {
        queueService.skip(currentUser.userId());
        return ok("donation.overlay_skipped");
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<ApiResponse<Void>> retry(@PathVariable("id") UUID overlayEventId) {
        queueService.retry(currentUser.userId(), overlayEventId);
        return ok("donation.overlay_retried");
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<Void>> retryAll(
            @RequestParam("fromStatus") OverlayEventStatus fromStatus) {
        queueService.retryAll(currentUser.userId(), fromStatus);
        return ok("donation.overlay_retried");
    }

    @DeleteMapping("/queue")
    public ResponseEntity<ApiResponse<Void>> purge() {
        queueService.purge(currentUser.userId());
        return ok("donation.overlay_purged");
    }

    private ResponseEntity<ApiResponse<Void>> ok(String messageKey) {
        return ResponseEntity.ok(new ApiResponse<>(messageHelper.get(messageKey), null));
    }
}
