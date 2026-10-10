package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.WithdrawalConfigResponse;

/** View konfigurasi penarikan efektif (HTTP response). */
public record WithdrawalConfigRes(
        long minAmount,
        long maxAmount,
        long fixedFee,
        int feePercentageBps,
        String currency
) {

    public static WithdrawalConfigRes from(WithdrawalConfigResponse c) {
        return new WithdrawalConfigRes(
                c.minAmount(),
                c.maxAmount(),
                c.fixedFee(),
                c.feePercentageBps(),
                c.currency());
    }
}
