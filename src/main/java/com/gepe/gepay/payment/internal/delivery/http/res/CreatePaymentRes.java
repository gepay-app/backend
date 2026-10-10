package com.gepe.gepay.payment.internal.delivery.http.res;

import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;

/** View hasil pembuatan pembayaran (payment + attempt) — HTTP response. */
public record CreatePaymentRes(
        PaymentRes payment,
        PaymentAttemptRes attempt
) {

    public static CreatePaymentRes from(CreatePaymentResult r) {
        return new CreatePaymentRes(
                PaymentRes.from(r.payment()),
                PaymentAttemptRes.from(r.attempt()));
    }
}
