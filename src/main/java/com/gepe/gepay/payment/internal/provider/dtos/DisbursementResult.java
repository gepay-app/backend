package com.gepe.gepay.payment.internal.provider.dtos;

import com.gepe.gepay.payment.api.enums.PayoutStatus;

import java.util.Map;

/**
 * Hasil pencairan dari provider. Disimpan payment ke {@code payouts}
 * ({@link #rawRequest}/{@link #rawResponse} untuk audit).
 *
 * @param providerReferenceId id transaksi di sisi PG (kunci matching webhook)
 * @param status              status netral hasil pemetaan dari status vendor
 * @param rawRequest          payload request aktual yang dikirim ke PG (audit)
 * @param rawResponse         payload response aktual dari PG (audit)
 */
public record DisbursementResult(
        String providerReferenceId,
        PayoutStatus status,
        Map<String, Object> rawRequest,
        Map<String, Object> rawResponse
) {
}
