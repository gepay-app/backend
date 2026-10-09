package com.gepe.gepay.payment.api.dtos;

import java.util.Map;
import java.util.UUID;

public record CreatePaymentCommand(
        String idempotencyKey,
        String type,
        UUID userId,
        UUID payerId,
        String channelCode,
        Long amount,
        String customerName,
        String customerEmail,
        Map<String, Object> metadata
) {
}
