package com.gepe.gepay.donation.internal.entity;

/**
 * Status donasi. Mengikuti status payment terkait; di-update lewat
 * {@code PaymentPaidEvent} (PAID) di fase overlay.
 */
public enum DonationStatus {
    PENDING,
    PAID,
    EXPIRED,
    FAILED
}
