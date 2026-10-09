package com.gepe.gepay.payment.internal.provider.midtrans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MidtransGetStatusResponse(
        String status_code,
        String status_message,
        String transaction_id,
        String order_id,
        String merchant_id,
        String gross_amount,
        String currency,
        String payment_type,
        String signature_key,
        String transaction_status,
        String fraud_status,
        String transaction_time,
        String expiry_time,

        // Field khusus Bank Transfer (VA)
        List<VaNumber> va_numbers,
        List<Object> payment_amounts,

        // Field khusus QRIS / E-Wallet
        String acquirer,
        String reference_id,
        List<Action> actions,

        // Field opsional penanganan channel/bank response
        String channel_response_code,
        String channel_response_message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaNumber(
            String bank,
            String va_number
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(
            String name,
            String method,
            String url
    ) {}
}