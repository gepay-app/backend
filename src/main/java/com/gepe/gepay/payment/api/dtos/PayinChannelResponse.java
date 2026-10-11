package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;

/**
 * Channel PAYIN untuk donor (katalog publik, tanpa auth). Sengaja hanya memuat
 * komponen fee yang ditanggung pembayar: katalog ini tidak boleh membocorkan
 * fee platform / hak creator (lihat {@code fee_configs} PLATFORM_PAYIN).
 *
 * <ul>
 *   <li>{@code minAmount}/{@code maxAmount} — batas route aktif (0 = tanpa batas).</li>
 *   <li>{@code pgFeeAmount}/{@code totalChargedAmount} — dihitung dari fee
 *       GATEWAY_PROCESSING; {@code null} bila {@code amount} tidak dikirim,
 *       sehingga klien menampilkan nominal tanpa rincian fee.</li>
 * </ul>
 */
public record PayinChannelResponse(
        Long id,
        String code,
        String displayName,
        ChannelType type,
        ChannelDirection direction,
        long minAmount,
        long maxAmount,
        Long pgFeeAmount,
        Long totalChargedAmount,
        String currency
) {
}
