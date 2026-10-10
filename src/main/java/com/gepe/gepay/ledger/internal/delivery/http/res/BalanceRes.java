package com.gepe.gepay.ledger.internal.delivery.http.res;

import com.gepe.gepay.ledger.api.dtos.BalanceResponse;

/** View saldo user (HTTP response). */
public record BalanceRes(
        long pending,
        long available,
        long withdrawalPayable,
        String currency
) {

    public static BalanceRes from(BalanceResponse b) {
        return new BalanceRes(b.pending(), b.available(), b.withdrawalPayable(), b.currency());
    }
}
