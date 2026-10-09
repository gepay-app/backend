package com.gepe.gepay.payment.internal.provider;

import com.gepe.gepay.payment.internal.provider.dtos.DisbursementRequest;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import com.gepe.gepay.payment.internal.provider.dtos.IncomingProviderNotification;
import com.gepe.gepay.payment.internal.provider.dtos.ProviderStatus;

import java.util.Map;

/**
 * SPI pencairan (payout). Tiap PG (Flip, Doku, dst.) punya satu implementasi.
 *
 * <p>Sama seperti {@link PayinProvider}, adapter ini hanya menembak API vendor;
 * fee dihitung service payment, bukan di sini. Verifikasi signature webhook
 * adalah tanggung jawab implementasi.
 */
public interface PayoutProvider {

    /** Kode provider yang cocok dengan {@code providers.code} (mis. {@code "FLIP"}). */
    String code();

    /** Mengirim dana ke rekening tujuan. */
    DisbursementResult disburse(DisbursementRequest request);

    /** Mengecek status pencairan terkini di PG. */
    ProviderStatus getStatus(String providerReferenceId);

    /**
     * Menerjemahkan webhook mentah menjadi {@link IncomingProviderNotification} kanonik.
     * Wajib memverifikasi signature; payload tidak valid harus ditolak.
     */
    IncomingProviderNotification parseWebhook(String rawBody, Map<String, String> headers);
}
