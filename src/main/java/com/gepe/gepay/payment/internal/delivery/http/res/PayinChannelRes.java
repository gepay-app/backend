package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.PayinChannelResponse;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;

/**
 * View channel PAYIN publik (HTTP response). Hanya memuat fee yang ditanggung
 * pembayar; fee platform/hak creator sengaja tidak diekspos.
 */
public record PayinChannelRes(
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

    public static PayinChannelRes from(PayinChannelResponse c) {
        return new PayinChannelRes(
                c.id(),
                c.code(),
                c.displayName(),
                c.type(),
                c.direction(),
                c.minAmount(),
                c.maxAmount(),
                c.pgFeeAmount(),
                c.totalChargedAmount(),
                c.currency());
    }
}
