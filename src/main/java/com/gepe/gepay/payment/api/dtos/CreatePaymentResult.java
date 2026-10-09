package com.gepe.gepay.payment.api.dtos;

public record CreatePaymentResult(
        PaymentResponse payment,
        PaymentAttemptResponse attempt
) {
}
