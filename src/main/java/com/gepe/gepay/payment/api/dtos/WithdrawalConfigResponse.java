package com.gepe.gepay.payment.api.dtos;

/**
 * Konfigurasi penarikan efektif untuk sebuah rekening tujuan. Dipakai klien
 * untuk memvalidasi nominal & menampilkan estimasi biaya sebelum mengirim
 * permintaan. Angka final tetap dari {@link WithdrawalResponse}.
 *
 * <ul>
 *   <li>{@code minAmount} / {@code maxAmount} — batas route channel (0 = tanpa batas).</li>
 *   <li>{@code fixedFee} + {@code feePercentageBps} — biaya PLATFORM_WITHDRAWAL aktif.</li>
 * </ul>
 */
public record WithdrawalConfigResponse(
        long minAmount,
        long maxAmount,
        long fixedFee,
        int feePercentageBps,
        String currency
) {
}
