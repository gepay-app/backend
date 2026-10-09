package com.gepe.gepay.payment.internal.provider.dtos;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Status terkini sebuah transaksi di sisi provider setelah dipetakan ke enum
 * netral. Dipakai untuk polling ketika webhook tidak datang.
 *
 * @param providerReferenceId id transaksi di sisi PG
 * @param status              status netral hasil pemetaan dari status vendor
 * @param paidAt              waktu pembayaran (bila sudah dibayar)
 * @param rawResponse         payload response aktual dari PG (audit)
 */
public record ProviderStatus(
        String providerReferenceId,
        PaymentAttemptStatus status,
        Instant paidAt,
        Map<String, Object> rawResponse
) {
}
