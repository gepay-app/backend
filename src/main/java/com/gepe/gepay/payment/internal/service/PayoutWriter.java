package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.enums.PayoutStatus;
import com.gepe.gepay.payment.api.enums.WithdrawalStatus;
import com.gepe.gepay.payment.internal.entity.Payout;
import com.gepe.gepay.payment.internal.entity.Withdrawal;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import com.gepe.gepay.payment.internal.repository.PayoutRepository;
import com.gepe.gepay.payment.internal.repository.WithdrawalRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Batas transaksi untuk eksekusi payout. Dipisah dari {@link PayoutService}
 * (orkestrator) supaya panggilan remote ke provider terjadi <strong>di
 * luar</strong> transaksi — lihat §3 transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutWriter {

    private final WithdrawalRepository withdrawalRepository;
    private final PayoutRepository payoutRepository;
    private final LedgerApi ledgerApi;

    /**
     * Mulai (REQUESTED -> PROCESSING + buat Payout PENDING) atau lanjutkan
     * payout yang sudah ada (PROCESSING -> reuse payout PENDING). Mengembalikan
     * id payout untuk dipakai sebagai idempotency key ke provider.
     */
    @Transactional
    public UUID beginPayout(UUID withdrawalId, Long providerId, long netAmount, long providerFeeAmount) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new ServiceException(PaymentError.WITHDRAWAL_NOT_FOUND, withdrawalId));

        if (withdrawal.getStatus() == WithdrawalStatus.PROCESSING) {
            return payoutRepository.findFirstByWithdrawalIdOrderByCreatedAtDesc(withdrawalId)
                    .map(Payout::getId)
                    .orElseThrow(() -> new ServiceException(PaymentError.PAYOUT_NOT_FOUND, withdrawalId));
        }

        withdrawal.markProcessing();
        withdrawalRepository.saveAndFlush(withdrawal);

        Payout payout = Payout.create(withdrawalId, providerId, netAmount, providerFeeAmount, null);
        payoutRepository.saveAndFlush(payout);
        return payout.getId();
    }

    /** Terapkan hasil disburse: status payout/withdrawal + jurnal J-6 (sukses) / J-7 (gagal). */
    @Transactional
    public void applyDisburseResult(UUID payoutId, String providerCode, DisbursementResult result) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ServiceException(PaymentError.PAYOUT_NOT_FOUND, payoutId));
        Withdrawal withdrawal = withdrawalRepository.findById(payout.getWithdrawalId())
                .orElseThrow(() -> new ServiceException(PaymentError.WITHDRAWAL_NOT_FOUND, payout.getWithdrawalId()));

        payout.recordProviderReference(result.providerReferenceId());
        payout.recordRawPayload(result.rawResponse());

        Instant at = Instant.now();
        if (result.status() == PayoutStatus.COMPLETED) {
            payout.markCompleted(at);
            withdrawal.markPaid(at);
            postJ6(payout, withdrawal, providerCode, at);
        } else if (result.status() == PayoutStatus.FAILED || result.status() == PayoutStatus.REVERSED) {
            payout.markFailed(null, null, at);
            withdrawal.markFailed(at);
            postJ7(payout, withdrawal, at);
        }
        // PENDING -> biarkan payout PENDING & withdrawal PROCESSING, retry run berikutnya.

        payoutRepository.saveAndFlush(payout);
        withdrawalRepository.saveAndFlush(withdrawal);
    }

    /** J-6: hold lunas; fee platform jadi pendapatan, fee provider jadi beban, float provider berkurang. */
    private void postJ6(Payout payout, Withdrawal withdrawal, String providerCode, Instant at) {
        long providerFee = payout.getProviderFeeAmount() == null ? 0L : payout.getProviderFeeAmount();
        long withdrawalFee = withdrawal.getWithdrawalFeeAmount() == null ? 0L : withdrawal.getWithdrawalFeeAmount();
        long net = payout.getAmount() == null ? 0L : payout.getAmount();
        String owner = withdrawal.getUserId().toString();

        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, owner, EntryDirection.DEBIT, withdrawal.getRequestedAmount()),
                new JournalLine(AccountCode.PAYOUT_FEE_EXPENSE, null, EntryDirection.DEBIT, providerFee),
                new JournalLine(AccountCode.WITHDRAWAL_FEE_REVENUE, null, EntryDirection.CREDIT, withdrawalFee),
                new JournalLine(AccountCode.PAYOUT_PROVIDER_FLOAT, providerCode, EntryDirection.CREDIT, net + providerFee)
        );

        ledgerApi.postJournal(
                "PAYOUT:" + payout.getId() + ":COMPLETED",
                JournalReferenceType.PAYOUT,
                payout.getId().toString(),
                "Payout completed for withdrawal " + withdrawal.getId(),
                at,
                lines,
                null
        );
    }

    /** J-7: payout gagal; kembalikan hold ke available. */
    private void postJ7(Payout payout, Withdrawal withdrawal, Instant at) {
        String owner = withdrawal.getUserId().toString();
        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, owner, EntryDirection.DEBIT, withdrawal.getRequestedAmount()),
                new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, owner, EntryDirection.CREDIT, withdrawal.getRequestedAmount())
        );

        ledgerApi.postJournal(
                "PAYOUT:" + payout.getId() + ":FAILED",
                JournalReferenceType.PAYOUT,
                payout.getId().toString(),
                "Payout failed, hold returned for withdrawal " + withdrawal.getId(),
                at,
                lines,
                null
        );
    }
}
