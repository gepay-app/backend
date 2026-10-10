package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.DonationType;
import com.gepe.gepay.donation.internal.entity.OverlayEvent;
import com.gepe.gepay.donation.internal.entity.OverlayEventStatus;
import com.gepe.gepay.donation.internal.repository.OverlayEventRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverlayQueueServiceTest {

    @Mock
    private OverlayEventRepository repository;
    @Mock
    private OverlayWriter overlayWriter;
    @Mock
    private OverlayStateStore stateStore;
    @Mock
    private OverlayDispatcher dispatcher;

    private OverlayQueueService service;

    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new OverlayQueueService(repository, overlayWriter, stateStore, dispatcher);
    }

    @Test
    void retriesOwnedEventAndDispatches() {
        OverlayEvent event = OverlayEvent.create(UUID.randomUUID(), creatorId, DonationType.TEXT, Map.of(), 30);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        service.retry(creatorId, event.getId());

        verify(overlayWriter).requeue(event.getId());
        verify(dispatcher).dispatch(creatorId);
    }

    @Test
    void rejectsRetryOfAnotherCreatorsEvent() {
        OverlayEvent event = OverlayEvent.create(UUID.randomUUID(), UUID.randomUUID(), DonationType.TEXT, Map.of(), 30);
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.retry(creatorId, event.getId()))
                .isInstanceOf(ServiceException.class);
        verify(overlayWriter, never()).requeue(any());
    }

    @Test
    void pauseAndResumeToggleState() {
        service.pause(creatorId);
        verify(stateStore).setPaused(creatorId, true);

        service.resume(creatorId);
        verify(stateStore).setPaused(creatorId, false);
        verify(dispatcher).dispatch(creatorId);
    }

    @Test
    void skipMarksCurrentAndAdvances() {
        OverlayEvent playing = OverlayEvent.create(UUID.randomUUID(), creatorId, DonationType.TEXT, Map.of(), 30);
        when(repository.findFirstByCreatorIdAndStatusOrderByCreatedAtAsc(creatorId, OverlayEventStatus.PLAYING))
                .thenReturn(Optional.of(playing));

        service.skip(creatorId);

        verify(overlayWriter).markSkipped(playing.getId());
        verify(stateStore).clearCurrent(creatorId);
        verify(dispatcher).dispatch(creatorId);
    }
}
