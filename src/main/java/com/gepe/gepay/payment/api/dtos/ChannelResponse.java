package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;

/**
 * Referensi channel pembayaran/pencairan yang aktif. {@code id} dipakai saat
 * membuat payout destination; {@code direction} memisahkan PAYIN vs PAYOUT.
 */
public record ChannelResponse(
        Long id,
        String code,
        String displayName,
        ChannelType type,
        ChannelDirection direction
) {
}
