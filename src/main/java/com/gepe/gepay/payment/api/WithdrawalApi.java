package com.gepe.gepay.payment.api;

import com.gepe.gepay.payment.api.dtos.PayoutDestinationCreateCommand;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationResponse;
import com.gepe.gepay.payment.api.dtos.WithdrawalCreateCommand;
import com.gepe.gepay.payment.api.dtos.WithdrawalResponse;

import java.util.List;
import java.util.UUID;

/**
 * Facade pencairan (withdrawal & payout destination). Pemilik (creator) selalu
 * diambil dari {@code identity.api.CurrentUser}, bukan dari payload — lihat
 * {@code AGENTS.md} §12.
 */
public interface WithdrawalApi {

    PayoutDestinationResponse createPayoutDestination(PayoutDestinationCreateCommand command);

    List<PayoutDestinationResponse> listPayoutDestinations();

    PayoutDestinationResponse setDefaultPayoutDestination(UUID destinationId);

    void deactivatePayoutDestination(UUID destinationId);

    /**
     * Minta tarik dana. Idempoten via {@code command.idempotencyKey()}; saldo
     * available di-hold (jurnal J-5) sampai payout selesai.
     */
    WithdrawalResponse createWithdrawal(WithdrawalCreateCommand command);

    WithdrawalResponse getWithdrawal(UUID withdrawalId);
}
