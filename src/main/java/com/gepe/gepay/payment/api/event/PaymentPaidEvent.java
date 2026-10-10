package com.gepe.gepay.payment.api.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Domain event diterbitkan saat sebuah {@code payment} berubah menjadi
 * {@code PAID}.
 *
 * <p>Diterbitkan dari {@code PaymentWebhookService} di dalam transaksi webhook;
 * listener consumer (mis. modul {@code donation}) wajib memakai
 * {@code @ApplicationModuleListener} sehingga dijalankan <strong>setelah commit</strong>
 * dan bersifat at-least-once — listener harus <strong>idempotent</strong> (lihat
 * {@code AGENTS.md} §3 events &amp; §11 multi-instance). Consumer tidak boleh membaca
 * tabel {@code payment.*} secara langsung; cukup pakai payload event ini.
 *
 * @param paymentId       id payment (UUID v7)
 * @param type            kode produk consumer, mis. {@code DONATION}
 * @param userId          user penerima hak (payee/creator)
 * @param grossAmount     nominal bruto pembayaran
 * @param netCreatorAmount hak bersih creator (setelah fee platform)
 * @param metadata        data netral consumer (bisa {@code null})
 * @param paidAt          waktu payment dianggap lunas (bisa {@code null} bila provider tidak mengirim)
 */
public record PaymentPaidEvent(
        UUID paymentId,
        String type,
        UUID userId,
        Long grossAmount,
        Long netCreatorAmount,
        Map<String, Object> metadata,
        Instant paidAt
) {
}
