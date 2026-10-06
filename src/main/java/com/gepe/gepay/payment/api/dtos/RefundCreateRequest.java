package com.gepe.gepay.payment.api.dtos;

import java.util.UUID;

/**
 * @param paymentId  id {@code payment.payments}
 * @param providerId id {@code payment.providers}
 */
public record RefundCreateRequest(
        UUID paymentId,
        Long providerId,
        Long amount,
        String reason
) {
}