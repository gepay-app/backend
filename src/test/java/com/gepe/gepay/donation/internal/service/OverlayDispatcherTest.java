package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.DonationType;
import com.gepe.gepay.donation.internal.entity.OverlayEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverlayDispatcherTest {

    @Mock
    private OverlayWriter overlayWriter;
    @Mock
    private OverlayStateStore stateStore;
    @Mock
    private OverlayBroadcaster broadcaster;

    private OverlayDispatcher dispatcher;

    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new OverlayDispatcher(overlayWriter, stateStore, broadcaster);
    }

    @Test
    void dispatchesOldestPendingWhenOnlineAndIdle() {
        when(stateStore.isPaused(creatorId)).thenReturn(false);
        when(overlayWriter.hasPlaying(creatorId)).thenReturn(false);
        when(stateStore.isDisplayOnline(creatorId)).thenReturn(true);
        OverlayEvent event = OverlayEvent.create(UUID.randomUUID(), creatorId, DonationType.TEXT, Map.of(), 30);
        when(overlayWriter.claimNextPending(creatorId)).thenReturn(Optional.of(event));

        dispatcher.dispatch(creatorId);

        verify(stateStore).setCurrent(creatorId, event.getId());
        verify(broadcaster).play(creatorId, event);
    }

    @Test
    void doesNotDispatchWhilePaused() {
        when(stateStore.isPaused(creatorId)).thenReturn(true);

        dispatcher.dispatch(creatorId);

        verifyNoInteractions(overlayWriter, broadcaster);
    }

    @Test
    void doesNotDispatchWhenSomethingAlreadyPlaying() {
        when(stateStore.isPaused(creatorId)).thenReturn(false);
        when(overlayWriter.hasPlaying(creatorId)).thenReturn(true);

        dispatcher.dispatch(creatorId);

        verify(overlayWriter, never()).claimNextPending(any());
        verifyNoInteractions(broadcaster);
    }

    @Test
    void doesNotDispatchWhenDisplayOffline() {
        when(stateStore.isPaused(creatorId)).thenReturn(false);
        when(overlayWriter.hasPlaying(creatorId)).thenReturn(false);
        when(stateStore.isDisplayOnline(creatorId)).thenReturn(false);

        dispatcher.dispatch(creatorId);

        verify(overlayWriter, never()).claimNextPending(any());
        verifyNoInteractions(broadcaster);
    }

    @Test
    void doesNothingWhenQueueEmpty() {
        when(stateStore.isPaused(creatorId)).thenReturn(false);
        when(overlayWriter.hasPlaying(creatorId)).thenReturn(false);
        when(stateStore.isDisplayOnline(creatorId)).thenReturn(true);
        when(overlayWriter.claimNextPending(creatorId)).thenReturn(Optional.empty());

        dispatcher.dispatch(creatorId);

        verifyNoInteractions(broadcaster);
        verify(stateStore, never()).setCurrent(any(), any());
    }
}
