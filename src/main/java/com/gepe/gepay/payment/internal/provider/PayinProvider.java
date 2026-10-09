package com.gepe.gepay.payment.internal.provider;

import com.gepe.gepay.payment.internal.provider.dtos.ChargeRequest;
import com.gepe.gepay.payment.internal.provider.dtos.ChargeResult;
import com.gepe.gepay.payment.internal.provider.dtos.IncomingProviderNotification;
import com.gepe.gepay.payment.internal.provider.dtos.ProviderStatus;

import java.util.Map;

/**
 * SPI pembayaran masuk (payin). Tiap PG (Midtrans, dst.) punya satu implementasi.
 *
 * <p>Adapter ini <strong>hanya menembak API vendor</strong>: menerjemahkan DTO
 * netral ke payload vendor dan sebaliknya. Perhitungan fee TIDAK di sini — itu
 * tanggung jawab service payment.
 *
 * <p>Tanggung jawab pemetaan status vendor ke enum netral dan verifikasi
 * signature webhook juga milik implementasi ini. {@link #parseWebhook} wajib
 * memverifikasi signature dan menolak payload yang tidak valid.
 */
public interface PayinProvider {

    /** Kode provider yang cocok dengan {@code providers.code} (mis. {@code "MIDTRANS"}). */
    String code();

    /** Membuat charge (VA/QRIS/dll) di sisi PG. */
    ChargeResult createCharge(ChargeRequest request);

    /** Mengecek status transaksi terkini di PG (fallback bila webhook tidak datang). */
    ProviderStatus getStatus(String providerReferenceId);

    /** Membatalkan transaksi yang belum dibayar. */
    void cancel(String providerReferenceId);

    /**
     * Menerjemahkan webhook mentah menjadi {@link IncomingProviderNotification} kanonik.
     * Wajib memverifikasi signature; payload tidak valid harus ditolak.
     */
    IncomingProviderNotification parseWebhook(String rawBody, Map<String, String> headers);
}
