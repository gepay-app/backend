package com.gepe.gepay.payment.internal.provider.dtos;

import com.gepe.gepay.payment.api.enums.ChannelType;

import java.time.Instant;
import java.util.Map;

/**
 * Perintah charge payin ke provider (vendor-agnostic). Fee TIDAK dihitung di
 * sini — {@code payment} sudah menghitungnya, jadi {@link #amount} adalah total
 * yang dibayar pembayar ({@code gross + pg_fee}).
 *
 * @param orderId              id yang kita kirim ke PG, = {@code payment_attempts.id} (UUID v7)
 * @param amount               total dibayar pembayar = gross + pg_fee
 * @param providerChannelCode  kode channel di sisi PG ({@code channel_routes.provider_channel_code})
 * @param channelType          kategori netral (VA/QRIS/EWALLET) untuk memilih endpoint
 * @param customerName         nama pembayar (opsional, tergantung channel)
 * @param customerEmail        email pembayar (opsional)
 * @param expiresAt            kedaluwarsa charge
 * @param metadata             data tambahan netral dari payment (bukan payload vendor)
 */
public record ChargeRequest(
        String orderId,
        long amount,
        String providerChannelCode,
        ChannelType channelType,
        String customerName,
        String customerEmail,
        Instant expiresAt,
        Map<String, Object> metadata
) {
}
