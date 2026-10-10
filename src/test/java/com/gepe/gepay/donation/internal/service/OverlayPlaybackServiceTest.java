package com.gepe.gepay.donation.internal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OverlayPlaybackServiceTest {

    @Mock
    private OverlayWriter overlayWriter;
    @Mock
    private OverlayStateStore stateStore;
    @Mock
    private OverlayDispatcher dispatcher;

    private OverlayPlaybackService playback;

    private final UUID creatorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        playback = new OverlayPlaybackService(overlayWriter, stateStore, dispatcher);
    }

    @Test
    void connectedMarksPresenceAndDispatches() {
        playback.connected(creatorId);
        verify(stateStore).touchDisplay(creatorId);
        verify(dispatcher).dispatch(creatorId);
    }

    @Test
    void heartbeatRefreshesPresence() {
        playback.heartbeat(creatorId);
        verify(stateStore).touchDisplay(creatorId);
    }

    @Test
    void disconnectedClearsPresence() {
        playback.disconnected(creatorId);
        verify(stateStore).clearDisplay(creatorId);
    }

    @Test
    void ackMarksPlayedAndAdvances() {
        UUID eventId = UUID.randomUUID();
        playback.ack(creatorId, eventId);
        verify(overlayWriter).markPlayedIfCurrent(creatorId, eventId);
        verify(stateStore).clearCurrent(creatorId);
        verify(dispatcher).dispatch(creatorId);
    }
}
