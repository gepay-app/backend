package com.gepe.gepay.ledger.api.dtos;

/**
 * Saldo user dalam rupiah. Semua angka dibaca dari akun ledger (materialized).
 *
 * <ul>
 *   <li>{@code pending} — hak creator yang sudah diakui tapi belum cair.</li>
 *   <li>{@code available} — saldo yang boleh ditarik.</li>
 *   <li>{@code withdrawalPayable} — dana yang sedang ditarik (hold).</li>
 * </ul>
 */
public record BalanceResponse(
        long pending,
        long available,
        long withdrawalPayable,
        String currency
) {
}
