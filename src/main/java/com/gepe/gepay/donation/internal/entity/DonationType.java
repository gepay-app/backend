package com.gepe.gepay.donation.internal.entity;

/**
 * Jenis donasi. Disimpan sebagai {@code varchar} (AGENTS.md §3).
 *
 * <p>{@code YOUTUBE} (bukan {@code VIDEO}) supaya nanti bisa ditambah tipe
 * upload video tanpa rename tipe ini.
 */
public enum DonationType {
    TEXT,
    YOUTUBE
}
