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
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import com.gepe.gepay.payment.internal.repository.PayoutRepository;
import com.gepe.gepay.payment.internal.repository.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutWriterTest {

    @Mock
    private WithdrawalRepository withdrawalRepository;
    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private LedgerApi ledgerApi;

    private PayoutWriter payoutWriter;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        payoutWriter = new PayoutWriter(withdrawalRepository, payoutRepository, ledgerApi);
    }

    @Test
    void beginPayout_Requested_CreatesPayoutAndMarksProcessing() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.REQUESTED, 90_000L, 3_000L, 87_000L);
        when(withdrawalRepository.findById(withdrawal.getId())).thenReturn(Optional.of(withdrawal));
        when(payoutRepository.saveAndFlush(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        UUID payoutId = payoutWriter.beginPayout(withdrawal.getId(), 2L, 87_000L, 2_500L);

        assertThat(payoutId).isNotNull();
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.PROCESSING);
        verify(payoutRepository).saveAndFlush(any(Payout.class));
    }

    @Test
    void beginPayout_Processing_ReusesExistingPayout() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.PROCESSING, 90_000L, 3_000L, 87_000L);
        when(withdrawalRepository.findById(withdrawal.getId())).thenReturn(Optional.of(withdrawal));

        Payout existing = Payout.create(withdrawal.getId(), 2L, 87_000L, 2_500L, null);
        when(payoutRepository.findFirstByWithdrawalIdOrderByCreatedAtDesc(withdrawal.getId()))
                .thenReturn(Optional.of(existing));

        UUID payoutId = payoutWriter.beginPayout(withdrawal.getId(), 2L, 87_000L, 2_500L);

        assertThat(payoutId).isEqualTo(existing.getId());
        verify(payoutRepository, never()).saveAndFlush(any(Payout.class));
    }

    @Test
    void applyDisburseResult_Completed_PostsBalancedJ6AndMarksPaid() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.PROCESSING, 90_000L, 3_000L, 87_000L);
        Payout payout = Payout.create(withdrawal.getId(), 2L, 87_000L, 2_500L, null);

        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(withdrawalRepository.findById(withdrawal.getId())).thenReturn(Optional.of(withdrawal));
        when(payoutRepository.saveAndFlush(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(withdrawalRepository.saveAndFlush(any(Withdrawal.class))).thenAnswer(inv -> inv.getArgument(0));

        DisbursementResult result = new DisbursementResult("FLIP-1", PayoutStatus.COMPLETED, Map.of(), Map.of());
        payoutWriter.applyDisburseResult(payout.getId(), "FLIP", result);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.COMPLETED);
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.PAID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerApi).postJournal(
                eq("PAYOUT:" + payout.getId() + ":COMPLETED"),
                eq(JournalReferenceType.PAYOUT),
                eq(payout.getId().toString()),
                anyString(),
                any(),
                captor.capture(),
                isNull()
        );

        List<JournalLine> lines = captor.getValue();
        long debit = lines.stream().filter(l -> l.direction() == EntryDirection.DEBIT).mapToLong(JournalLine::amount).sum();
        long credit = lines.stream().filter(l -> l.direction() == EntryDirection.CREDIT).mapToLong(JournalLine::amount).sum();
        // debit 90.000 (hold) + 2.500 (payout fee) ; credit 3.000 (withdrawal fee) + 89.500 (float)
        assertThat(debit).isEqualTo(credit).isEqualTo(92_500L);
    }

    @Test
    void applyDisburseResult_Failed_PostsBalancedJ7AndMarksFailed() {
        Withdrawal withdrawal = withdrawal(WithdrawalStatus.PROCESSING, 90_000L, 3_000L, 87_000L);
        Payout payout = Payout.create(withdrawal.getId(), 2L, 87_000L, 2_500L, null);

        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(withdrawalRepository.findById(withdrawal.getId())).thenReturn(Optional.of(withdrawal));
        when(payoutRepository.saveAndFlush(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(withdrawalRepository.saveAndFlush(any(Withdrawal.class))).thenAnswer(inv -> inv.getArgument(0));

        DisbursementResult result = new DisbursementResult("FLIP-2", PayoutStatus.FAILED, Map.of(), Map.of());
        payoutWriter.applyDisburseResult(payout.getId(), "FLIP", result);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(withdrawal.getStatus()).isEqualTo(WithdrawalStatus.FAILED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerApi).postJournal(
                eq("PAYOUT:" + payout.getId() + ":FAILED"),
                eq(JournalReferenceType.PAYOUT),
                eq(payout.getId().toString()),
                anyString(),
                any(),
                captor.capture(),
                isNull()
        );

        List<JournalLine> lines = captor.getValue();
        long debit = lines.stream().filter(l -> l.direction() == EntryDirection.DEBIT).mapToLong(JournalLine::amount).sum();
        long credit = lines.stream().filter(l -> l.direction() == EntryDirection.CREDIT).mapToLong(JournalLine::amount).sum();
        assertThat(debit).isEqualTo(credit).isEqualTo(90_000L);
        assertThat(lines).anyMatch(l -> l.accountCode() == AccountCode.WITHDRAWAL_PAYABLE && l.direction() == EntryDirection.DEBIT);
        assertThat(lines).anyMatch(l -> l.accountCode() == AccountCode.CREATOR_PAYABLE_AVAILABLE && l.direction() == EntryDirection.CREDIT);
    }

    private Withdrawal withdrawal(WithdrawalStatus status, long requested, long fee, long net) {
        Withdrawal w = Withdrawal.create("WD-" + UUID.randomUUID(), userId, UUID.randomUUID(), 1L,
                requested, "570000002233331", "GePe Dev", "bri");
        w.applyFeeSnapshot(fee, net);
        if (status == WithdrawalStatus.PROCESSING) {
            w.markProcessing();
        }
        return w;
    }
}
