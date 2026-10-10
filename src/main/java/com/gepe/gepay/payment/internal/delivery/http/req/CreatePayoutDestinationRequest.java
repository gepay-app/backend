package com.gepe.gepay.payment.internal.delivery.http.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePayoutDestinationRequest(
        @NotNull Long channelId,
        @NotBlank String accountNumber,
        @NotBlank String accountName,
        @NotBlank String bankCode
) {
}
