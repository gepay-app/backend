package com.gepe.gepay.payment.internal.provider.dtos;

import java.time.Instant;
import java.util.Map;

/**
 * Hasil charge payin dari provider. Dibuat oleh adapter; disimpan oleh payment
 * ke {@code payment_attempts} (lihat {@link #rawRequest}/{@link #rawResponse}
 * yang memetakan ke kolom {@code raw_request}/{@code raw_response}).
 *
 * @param orderId                 sama dengan {@link ChargeRequest#orderId()}
 * @param providerReferenceId     id/order_id di sisi PG (kunci matching webhook & CSV)
 * @param paymentReferenceNumber  nomor yang ditampilkan ke pembayar (no. VA / QR string / redirect URL)
 * @param expiresAt               kedaluwarsa menurut PG
 * @param rawRequest              payload request aktual yang dikirim ke PG (audit)
 * @param rawResponse             payload response aktual dari PG (audit)
 */
public record ChargeResult(
        String orderId,
        String providerReferenceId,
        String paymentReferenceNumber,
        Instant expiresAt,
        Map<String, Object> rawRequest,
        Map<String, Object> rawResponse
) {
}
