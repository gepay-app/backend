package com.gepe.gepay.payment.internal.provider.dtos;

import java.util.Map;

/**
 * Perintah pencairan (payout) ke provider (vendor-agnostic). Sama seperti payin,
 * fee tidak dihitung di sini.
 *
 * @param idempotencyKey       kunci idempoten agar retry tidak menggandakan pencairan
 * @param amount               nominal yang dikirim ke rekening tujuan
 * @param providerChannelCode  kode channel tujuan di sisi PG (mis. {@code "BCA"})
 * @param accountNumber        nomor rekening tujuan
 * @param accountName          nama pemilik rekening tujuan
 * @param remark               keterangan transfer
 * @param metadata             data tambahan netral dari payment
 */
public record DisbursementRequest(
        String idempotencyKey,
        long amount,
        String providerChannelCode,
        String accountNumber,
        String accountName,
        String remark,
        Map<String, Object> metadata
) {
}
