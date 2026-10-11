package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.PayoutChannelResponse;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;

/**
 * View channel PAYOUT (HTTP response, owner). Tarif penarikan efektif sudah
 * memperhitungkan {@code UserFeeOverride} milik user pemanggil.
 */
public record PayoutChannelRes(
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

    public static PayoutChannelRes from(PayoutChannelResponse c) {
        return new PayoutChannelRes(
                c.id(),
                c.code(),
                c.displayName(),
                c.type(),
                c.direction(),
                c.minAmount(),
                c.maxAmount(),
                c.withdrawalFeeAmount(),
                c.feePercentageBps(),
                c.currency());
    }
}
