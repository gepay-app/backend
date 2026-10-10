package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.dto.OverlayQueueItem;
import com.gepe.gepay.donation.internal.entity.OverlayEvent;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.OverlayEventRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Operasi control overlay untuk satu creator (pause/resume/skip/retry/purge).
 * Parameter {@code creatorId} eksplisit supaya bisa dipakai dari HTTP (diisi
 * dari {@code CurrentUser}) maupun dari thread WebSocket (diisi dari sesi hasil
 * resolusi key/token). Transisi status tetap lewat {@link OverlayWriter}.
 */
@Service
@RequiredArgsConstructor
public class OverlayQueueService {

    private final OverlayEventRepository repository;
    private final OverlayWriter overlayWriter;
    private final OverlayStateStore stateStore;
    private final OverlayDispatcher dispatcher;

    @Transactional(readOnly = true)
    public List<OverlayQueueItem> list(UUID creatorId, OverlayEventStatus status) {
        List<OverlayEvent> events = status == null
                ? repository.findByCreatorIdOrderByCreatedAtAsc(creatorId)
                : repository.findByCreatorIdAndStatusOrderByCreatedAtAsc(creatorId, status);
        return events.stream().map(this::toItem).toList();
    }

    public void pause(UUID creatorId) {
        stateStore.setPaused(creatorId, true);
    }

    public void resume(UUID creatorId) {
        stateStore.setPaused(creatorId, false);
        dispatcher.dispatch(creatorId);
    }

    /** Lewati event yang sedang PLAYING (atau PENDING berikutnya) dan lanjut. */
    public void skip(UUID creatorId) {
        repository.findFirstByCreatorIdAndStatusOrderByCreatedAtAsc(creatorId, OverlayEventStatus.PLAYING)
                .or(() -> repository.findFirstByCreatorIdAndStatusOrderByCreatedAtAsc(creatorId, OverlayEventStatus.PENDING))
                .ifPresent(event -> overlayWriter.markSkipped(event.getId()));
        stateStore.clearCurrent(creatorId);
        dispatcher.dispatch(creatorId);
    }

    public void retry(UUID creatorId, UUID overlayEventId) {
        requireOwned(creatorId, overlayEventId);
        overlayWriter.requeue(overlayEventId);
        dispatcher.dispatch(creatorId);
    }

    public int retryAll(UUID creatorId, OverlayEventStatus fromStatus) {
        int count = overlayWriter.requeueAll(creatorId, fromStatus);
        dispatcher.dispatch(creatorId);
        return count;
    }

    public int purge(UUID creatorId) {
        stateStore.clearCurrent(creatorId);
        return overlayWriter.purgePending(creatorId);
    }

    private void requireOwned(UUID creatorId, UUID overlayEventId) {
        OverlayEvent event = repository.findById(overlayEventId)
                .orElseThrow(() -> new ServiceException(DonationError.OVERLAY_EVENT_NOT_FOUND, overlayEventId));
        if (!event.getCreatorId().equals(creatorId)) {
            throw new ServiceException(DonationError.OVERLAY_NOT_OWNED);
        }
    }

    private OverlayQueueItem toItem(OverlayEvent event) {
        Object donorName = event.getPayload() == null ? null : event.getPayload().get("donorName");
        Object amount = event.getPayload() == null ? null : event.getPayload().get("amount");
        return new OverlayQueueItem(
                event.getId(),
                event.getDonationId(),
                event.getType().name(),
                event.getStatus().name(),
                amount instanceof Number number ? number.longValue() : 0L,
                donorName == null ? null : donorName.toString(),
                event.getDurationSeconds(),
                event.getAttempts(),
                event.getCreatedAt(),
                event.getPlayedAt());
    }
}
