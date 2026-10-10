package com.gepe.gepay.payment.api.dtos;

import java.time.Instant;
import java.util.UUID;

public record PayoutDestinationResponse(
        UUID id,
        Long channelId,
        String accountNumber,
        String accountName,
        String bankCode,
        Boolean isDefault,
        Boolean isActive,
        Instant createdAt
) {
}
