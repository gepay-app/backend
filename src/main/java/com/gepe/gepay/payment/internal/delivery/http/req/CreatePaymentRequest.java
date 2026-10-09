package com.gepe.gepay.payment.internal.delivery.http.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotBlank String idempotencyKey,
        @NotBlank String type,
        @NotNull UUID userId,
        UUID payerId,
        @NotBlank String channelCode,
        @NotNull @Min(1) Long amount,
        String customerName,
        String customerEmail,
        Map<String, Object> metadata
) {
}
