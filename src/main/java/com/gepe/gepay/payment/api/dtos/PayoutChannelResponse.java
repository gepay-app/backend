package com.gepe.gepay.payment.api.dtos;

import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;

/**
 * Channel PAYOUT untuk creator (butuh auth: fee efektif bergantung pada
 * {@code UserFeeOverride} milik user, mis. creator VIP). Nominal biaya penuh
 * tidak bisa dihitung tanpa jumlah penarikan, jadi hanya tarif yang
 * dikembalikan: {@code withdrawalFeeAmount} (fixed) + {@code feePercentageBps}.
 *
 * <ul>
 *   <li>{@code minAmount}/{@code maxAmount} — batas route aktif (0 = tanpa batas).</li>
 *   <li>{@code withdrawalFeeAmount}/{@code feePercentageBps} — tarif
 *       PLATFORM_WITHDRAWAL efektif, sudah memperhitungkan override bila ada.</li>
 * </ul>
 */
public record PayoutChannelResponse(
        Long id,
        String code,
        String displayName,
        ChannelType type,
        ChannelDirection direction,
        long minAmount,
        long maxAmount,
        long withdrawalFeeAmount,
        int feePercentageBps,
        String currency
) {
}
