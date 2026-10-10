package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.enums.EvidenceSource;
import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.internal.entity.Payment;
import com.gepe.gepay.payment.internal.entity.Provider;
import com.gepe.gepay.payment.internal.entity.Settlement;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.repository.PaymentRepository;
import com.gepe.gepay.payment.internal.repository.ProviderRepository;
import com.gepe.gepay.payment.internal.repository.SettlementRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Batas transaksi untuk satu grup settlement (satu {@code provider_id} +
 * {@code settlement_target}). Membuat header {@code settlements} langsung
 * {@code CONFIRMED} (mode portofolio: {@code actual = expected}, variance 0),
 * lalu memposting J-2 + J-3 dan menandai tiap payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementWriter {

    private static final String BANK_OPERATING_OWNER = "BANK-1";

    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;
    private final ProviderRepository providerRepository;
    private final LedgerApi ledgerApi;

    @Transactional
    public void settleGroup(Long providerId, SettlementTarget settlementTarget, List<UUID> paymentIds) {
        List<Payment> payments = paymentRepository.findAllById(paymentIds).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID && p.getSettlementId() == null)
                .toList();
        if (payments.isEmpty()) {
            log.debug("Group providerId={} target={} has no unsettled payments; skipping", providerId, settlementTarget);
            return;
        }

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> {
                    log.error("Settlement provider {} referenced by payments is missing", providerId);
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        long expectedAmount = payments.stream().mapToLong(Payment::getExpectedSettlementAmount).sum();

        Settlement settlement = Settlement.create(
                providerId, settlementTarget, null, EvidenceSource.SYSTEM, null, null, null, null);
        settlement.recordEvidence(
                "system-auto:" + LocalDate.now(),
                Map.of(
                        "mode", "portfolio",
                        "assumption", "expected amount treated as actual; variance always 0",
                        "payment_ids", payments.stream().map(p -> p.getId().toString()).toList()
                )
        );
        settlement.matchExpected(expectedAmount);
        settlement.confirm(expectedAmount, Instant.now());
        settlementRepository.saveAndFlush(settlement);

        postJ2(settlement, provider, expectedAmount);
        postJ3(settlement, payments);

        Instant settledAt = Instant.now();
        payments.forEach(p -> p.markSettled(settlement.getId(), settledAt));
        paymentRepository.saveAll(payments);

        log.info("Settled group providerId={} target={}: settlementId={}, expectedAmount={}, payments={}",
                providerId, settlementTarget, settlement.getId(), expectedAmount, payments.size());
    }

    /** J-2: piutang PG dianggap cair ke rekening tujuan (bank / saldo provider). */
    private void postJ2(Settlement settlement, Provider provider, long amount) {
        AccountCode debitAccount = settlement.getSettlementTarget() == SettlementTarget.BANK
                ? AccountCode.BANK_OPERATING
                : AccountCode.PAYIN_PROVIDER_BALANCE;
        String debitOwner = settlement.getSettlementTarget() == SettlementTarget.BANK
                ? BANK_OPERATING_OWNER
                : provider.getCode();

        List<JournalLine> lines = List.of(
                new JournalLine(debitAccount, debitOwner, EntryDirection.DEBIT, amount),
                new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, provider.getCode(), EntryDirection.CREDIT, amount)
        );

        ledgerApi.postJournal(
                "SETTLEMENT:" + settlement.getId() + ":CONFIRMED",
                JournalReferenceType.SETTLEMENT,
                settlement.getId().toString(),
                "Settlement confirmed (portfolio auto) for provider " + provider.getCode(),
                settlement.getActualSettledAt(),
                lines,
                null
        );
    }

    /** J-3: hak tiap creator dipindah pending -> available (satu jurnal per creator). */
    private void postJ3(Settlement settlement, List<Payment> payments) {
        Map<UUID, Long> netByCreator = payments.stream()
                .collect(Collectors.groupingBy(Payment::getUserId, Collectors.summingLong(Payment::getNetCreatorAmount)));

        for (Map.Entry<UUID, Long> entry : netByCreator.entrySet()) {
            UUID userId = entry.getKey();
            long net = entry.getValue();
            String owner = userId.toString();

            List<JournalLine> lines = List.of(
                    new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, owner, EntryDirection.DEBIT, net),
                    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, owner, EntryDirection.CREDIT, net)
            );

            ledgerApi.postJournal(
                    "SETTLEMENT:" + settlement.getId() + ":RELEASE:" + userId,
                    JournalReferenceType.SETTLEMENT,
                    settlement.getId().toString(),
                    "Release creator funds for user " + userId,
                    settlement.getActualSettledAt(),
                    lines,
                    null
            );
        }
    }
}
