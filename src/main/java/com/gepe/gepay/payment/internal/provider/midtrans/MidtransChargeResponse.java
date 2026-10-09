package com.gepe.gepay.payment.internal.provider.midtrans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MidtransChargeResponse(
        String status_code,
        String status_message,
        String transaction_id,
        String order_id,
        String merchant_id,
        String gross_amount,
        String currency,
        String payment_type,
        String transaction_time,
        String transaction_status,
        String fraud_status,
        List<Action> actions,
        List<VaNumber> va_numbers,
        String qr_string,
        String expiry_time,
        String channel_response_code,
        String channel_response_message,
        String acquirer
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Action(
            String name,
            String method,
            String url
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VaNumber(
            String bank,
            String va_number
    ) {}
}
