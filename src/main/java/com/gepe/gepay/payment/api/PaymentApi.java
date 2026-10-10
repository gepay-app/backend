package com.gepe.gepay.payment.api;

import com.gepe.gepay.payment.api.dtos.ChannelResponse;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.payment.api.dtos.PaymentResponse;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.platform.web.response.PageResponse;

import java.util.List;
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

    /** Pembayaran masuk (earnings) milik current user, terbaru lebih dulu. */
    PageResponse<PaymentResponse> listPayments(int page, int size);

    /** Channel aktif dengan arah tertentu (mis. {@code PAYOUT} untuk rekening tujuan). */
    List<ChannelResponse> listChannels(ChannelDirection direction);
}
