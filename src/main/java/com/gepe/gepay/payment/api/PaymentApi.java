package com.gepe.gepay.payment.api;

import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.payment.api.dtos.PaymentResponse;

import java.util.UUID;

public interface PaymentApi {

    /**
     * Creates a new generic payment and initiates a charge attempt with the provider.
     * Idempotent via {@code command.idempotencyKey()}.
     */
    CreatePaymentResult createPayment(CreatePaymentCommand command);

    /**
     * Retrieves payment details by payment ID.
     */
    PaymentResponse getPayment(UUID paymentId);
}
