package com.gepe.gepay.payment.internal.provider.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO Jackson fleksibel untuk memetik seluruh variasi field webhook Midtrans
 * (GoPay, QRIS, ShopeePay, Bank Transfer VA Permata/BCA/BNI/BRI).
 */
@JsonIgnoreProperties(ignoreUnknown = true) // biar ga error jika ada field baru dari midtarns
public record MidtransNotificationPayload(
        String transaction_time,
        String transaction_status,
        String transaction_id,
        String status_message,
        String status_code,
        String signature_key,
        String settlement_time,
        String payment_type,
        String order_id,
        String merchant_id,
        String gross_amount,
        String fraud_status,
        String currency,
        // Channel specific: Bank Transfer (Permata vs Others)
        String permata_va_number,
        List<VaNumber> va_numbers,
        List<PaymentAmount> payment_amounts,
        // Channel specific: QRIS
        String transaction_type,
        String acquirer,
        String issuer,
        String merchant_cross_reference_id
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaNumber(
            String va_number,
            String bank
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentAmount(
            String paid_at,
            String amount
    ) {
    }
}
