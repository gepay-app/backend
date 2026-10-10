package com.gepe.gepay.donation.internal.delivery.http;

import com.gepe.gepay.donation.internal.delivery.http.res.OverlayQueueItemRes;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import com.gepe.gepay.donation.internal.service.OverlayQueueService;
import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Overlay control", description = "OBS overlay queue control (owner)")
@SecurityRequirement(name = "bearerAuth")
public class OverlayControlController {

    private final OverlayQueueService queueService;
    private final CurrentUser currentUser;
    private final MessageHelper messageHelper;

    @GetMapping("/queue")
    @Operation(summary = "List the overlay queue (optional status filter)")
    public ResponseEntity<ApiResponse<List<OverlayQueueItemRes>>> queue(
            @RequestParam(value = "status", required = false) OverlayEventStatus status) {
        List<OverlayQueueItemRes> items = queueService.list(currentUser.userId(), status)
                .stream().map(OverlayQueueItemRes::from).toList();
        return ResponseEntity.ok(new ApiResponse<>(messageHelper.get("donation.overlay_queue_retrieved"), items));
    }

    @PostMapping("/pause")
    @Operation(summary = "Pause overlay playback")
    public ResponseEntity<ApiResponse<Void>> pause() {
        queueService.pause(currentUser.userId());
        return ok("donation.overlay_paused");
    }

    @PostMapping("/resume")
    @Operation(summary = "Resume overlay playback")
    public ResponseEntity<ApiResponse<Void>> resume() {
        queueService.resume(currentUser.userId());
        return ok("donation.overlay_resumed");
    }

    @PostMapping("/skip")
    @Operation(summary = "Skip the currently playing overlay")
    public ResponseEntity<ApiResponse<Void>> skip() {
        queueService.skip(currentUser.userId());
        return ok("donation.overlay_skipped");
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Re-queue a single overlay event")
    public ResponseEntity<ApiResponse<Void>> retry(@PathVariable("id") UUID overlayEventId) {
        queueService.retry(currentUser.userId(), overlayEventId);
        return ok("donation.overlay_retried");
    }

    @PostMapping("/retry")
    @Operation(summary = "Re-queue all overlay events from a given status")
    public ResponseEntity<ApiResponse<Void>> retryAll(
            @RequestParam("fromStatus") OverlayEventStatus fromStatus) {
        queueService.retryAll(currentUser.userId(), fromStatus);
        return ok("donation.overlay_retried");
    }

    @DeleteMapping("/queue")
    @Operation(summary = "Purge pending overlay events")
    public ResponseEntity<ApiResponse<Void>> purge() {
        queueService.purge(currentUser.userId());
        return ok("donation.overlay_purged");
    }

    private ResponseEntity<ApiResponse<Void>> ok(String messageKey) {
        return ResponseEntity.ok(new ApiResponse<>(messageHelper.get(messageKey), null));
    }
}
