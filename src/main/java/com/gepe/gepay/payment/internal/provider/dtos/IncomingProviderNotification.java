package com.gepe.gepay.payment.internal.provider.dtos;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.api.enums.ProcessedEventType;

import java.time.Instant;
import java.util.Map;

/**
 * Snapshot event kanonik hasil verifikasi dan terjemahan webhook dari vendor (PG).
 * Dipakai baik untuk payin maupun payout.
 *
 * @param type                jenis event netral (PAYMENT_PAID, PAYMENT_FAILED, ...)
 * @param providerReferenceId ID transaksi unik di sisi PG (misal Midtrans transaction_id)
 * @param attemptStatus       status netral attempt setelah event ini
 * @param amount              nominal transaksi dalam bentuk Long (nullable jika tidak ada)
 * @param occurredAt          waktu kejadian di sisi vendor
 * @param raw                 payload webhook mentah (untuk audit & simpan di processed_events)
 */
public record IncomingProviderNotification(
        ProcessedEventType type,
        String providerReferenceId,
        PaymentAttemptStatus attemptStatus,
        Long amount,
        Instant occurredAt,
        Map<String, Object> raw
) {
}
