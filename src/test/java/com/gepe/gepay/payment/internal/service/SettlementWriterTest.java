package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.enums.EvidenceSource;
import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.api.enums.SettlementStatus;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.internal.entity.Payment;
import com.gepe.gepay.payment.internal.entity.Provider;
import com.gepe.gepay.payment.internal.entity.Settlement;
import com.gepe.gepay.payment.internal.repository.PaymentRepository;
import com.gepe.gepay.payment.internal.repository.ProviderRepository;
import com.gepe.gepay.payment.internal.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementWriterTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private LedgerApi ledgerApi;

    private SettlementWriter settlementWriter;

    private final UUID creatorA = UUID.randomUUID();
    private final UUID creatorB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        settlementWriter = new SettlementWriter(paymentRepository, settlementRepository, providerRepository, ledgerApi);
    }

    @Test
    void settleGroup_CreatesConfirmedBatch_PostsBalancedJ2AndJ3PerCreator() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        Payment p1 = paidPayment("IDEM-1", creatorA, 100_000L);
        Payment p2 = paidPayment("IDEM-2", creatorA, 200_000L);
        Payment p3 = paidPayment("IDEM-3", creatorB, 50_000L);
        when(paymentRepository.findAllById(anyList())).thenReturn(List.of(p1, p2, p3));

        settlementWriter.settleGroup(1L, SettlementTarget.BANK, List.of(p1.getId(), p2.getId(), p3.getId()));

        // Batch CONFIRMED dengan variance 0.
        ArgumentCaptor<Settlement> settlementCaptor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository).saveAndFlush(settlementCaptor.capture());
        Settlement batch = settlementCaptor.getValue();
        assertThat(batch.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(batch.getExpectedAmount()).isEqualTo(350_000L);
        assertThat(batch.getActualAmount()).isEqualTo(350_000L);
        assertThat(batch.getVarianceAmount()).isZero();
        assertThat(batch.getEvidenceSource()).isEqualTo(EvidenceSource.SYSTEM);

        // J-2 (1) + J-3 (2 creator) = 3 jurnal.
        ArgumentCaptor<List<JournalLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerApi, times(3)).postJournal(anyString(), any(), any(), any(), any(), linesCaptor.capture(), isNull());
        List<List<JournalLine>> allLines = linesCaptor.getAllValues();

        // J-2: debit BANK_OPERATING, credit PG_CLEARING_RECEIVABLE (balanced).
        List<JournalLine> j2 = allLines.get(0);
        assertBalanced(j2, 350_000L);
        assertThat(debit(j2, AccountCode.BANK_OPERATING)).isEqualTo(350_000L);
        assertThat(credit(j2, AccountCode.PG_CLEARING_RECEIVABLE)).isEqualTo(350_000L);

        // J-3: satu jurnal per creator, agregasi net yang benar. Urutan grup
        // tidak dijamin (HashMap key UUID), jadi dicek per-owner, bukan per-index.
        List<List<JournalLine>> j3Journals = allLines.subList(1, allLines.size());
        assertThat(j3Journals).hasSize(2);
        for (List<JournalLine> j3 : j3Journals) {
            long pending = debit(j3, AccountCode.CREATOR_PAYABLE_PENDING);
            long available = credit(j3, AccountCode.CREATOR_PAYABLE_AVAILABLE);
            assertThat(pending).isEqualTo(available).isPositive();
        }
        Map<String, Long> pendingByOwner = j3Journals.stream()
                .flatMap(List::stream)
                .filter(l -> l.accountCode() == AccountCode.CREATOR_PAYABLE_PENDING
                        && l.direction() == EntryDirection.DEBIT)
                .collect(Collectors.toMap(JournalLine::ownerRef, JournalLine::amount));
        assertThat(pendingByOwner)
                .containsEntry(creatorA.toString(), 300_000L)
                .containsEntry(creatorB.toString(), 50_000L);

        // Semua payment ditandai settled.
        assertThat(p1.getSettlementId()).isEqualTo(batch.getId());
        assertThat(p2.getSettlementId()).isEqualTo(batch.getId());
        assertThat(p3.getSettlementId()).isEqualTo(batch.getId());
        verify(paymentRepository).saveAll(anyList());
    }

    @Test
    void settleGroup_SkipsAlreadySettledPayments() {
        Payment alreadySettled = paidPayment("IDEM-9", creatorA, 100_000L);
        alreadySettled.markSettled(UUID.randomUUID(), Instant.now());

        when(paymentRepository.findAllById(anyList())).thenReturn(List.of(alreadySettled));

        settlementWriter.settleGroup(1L, SettlementTarget.BANK, List.of(alreadySettled.getId()));

        verify(settlementRepository, never()).saveAndFlush(any());
        verify(ledgerApi, never()).postJournal(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void settleGroup_ProviderBalanceTarget_DebitsProviderBalanceAccount() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));

        Payment p = paidPayment("IDEM-4", creatorA, 100_000L);
        when(paymentRepository.findAllById(anyList())).thenReturn(List.of(p));

        settlementWriter.settleGroup(1L, SettlementTarget.PROVIDER_BALANCE, List.of(p.getId()));

        ArgumentCaptor<List<JournalLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerApi, times(2)).postJournal(anyString(), any(), any(), any(), any(), linesCaptor.capture(), isNull());
        List<JournalLine> j2 = linesCaptor.getAllValues().get(0);
        assertThat(debit(j2, AccountCode.PAYIN_PROVIDER_BALANCE)).isEqualTo(100_000L);
        assertThat(credit(j2, AccountCode.PG_CLEARING_RECEIVABLE)).isEqualTo(100_000L);
    }

    private Payment paidPayment(String idem, UUID userId, long gross) {
        Payment p = Payment.create(idem, "DONATION", userId, null, 1L, 1L, 1L, gross);
        p.applyFeeSnapshot(null, 0L, 0, 0L, 0, 0L, 0L, null, null, 0L, 0, 0L, 0, 0L, 0L, gross, gross, gross);
        p.markPaid(Instant.now());
        return p;
    }

    private long debit(List<JournalLine> lines, AccountCode code) {
        return lines.stream()
                .filter(l -> l.accountCode() == code && l.direction() == EntryDirection.DEBIT)
                .mapToLong(JournalLine::amount).sum();
    }

    private long credit(List<JournalLine> lines, AccountCode code) {
        return lines.stream()
                .filter(l -> l.accountCode() == code && l.direction() == EntryDirection.CREDIT)
                .mapToLong(JournalLine::amount).sum();
    }

    private void assertBalanced(List<JournalLine> lines, long expectedTotal) {
        long debit = lines.stream().filter(l -> l.direction() == EntryDirection.DEBIT).mapToLong(JournalLine::amount).sum();
        long credit = lines.stream().filter(l -> l.direction() == EntryDirection.CREDIT).mapToLong(JournalLine::amount).sum();
        assertThat(debit).isEqualTo(credit).isEqualTo(expectedTotal);
    }
}
