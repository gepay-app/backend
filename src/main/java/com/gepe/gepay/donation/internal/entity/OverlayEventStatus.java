package com.gepe.gepay.donation.internal.entity;

/**
 * Status satu item antrean overlay (state machine, lihat {@code todo.md} §5).
 */
public enum OverlayEventStatus {
    PENDING,
    PLAYING,
    PLAYED,
    SKIPPED,
    FAILED
}
