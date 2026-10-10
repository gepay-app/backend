package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.ChannelResponse;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;

/** View channel pembayaran/pencairan (HTTP response). */
public record ChannelRes(
        Long id,
        String code,
        String displayName,
        ChannelType type,
        ChannelDirection direction
) {

    public static ChannelRes from(ChannelResponse c) {
        return new ChannelRes(
                c.id(),
                c.code(),
                c.displayName(),
                c.type(),
                c.direction());
    }
}
