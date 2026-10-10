package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.internal.entity.Payment;
import com.gepe.gepay.payment.internal.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orkestrator settlement otomatis. Sengaja <strong>TIDAK</strong>
 * {@code @Transactional}: ia hanya memilih kandidat lalu mengelompokkannya;
 * penulisan DB (jurnal + state) didelegasikan ke {@link SettlementWriter} yang
 * menjadi batas transaksi per grup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final PaymentRepository paymentRepository;
    private final SettlementWriter settlementWriter;

    public void settleDuePayments(LocalDate today) {
        List<Payment> candidates = paymentRepository.findSettlementCandidates(PaymentStatus.PAID, today);
        if (candidates.isEmpty()) {
            log.info("No payments due for settlement on {}", today);
            return;
        }

        Map<GroupKey, List<Payment>> groups = candidates.stream()
                .collect(Collectors.groupingBy(
                        p -> new GroupKey(p.getProviderId(), p.getChannelRoute().getSettlementTarget())
                ));

        for (Map.Entry<GroupKey, List<Payment>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            List<UUID> paymentIds = entry.getValue().stream().map(Payment::getId).toList();
            log.info("Settling group providerId={} target={} with {} payment(s)",
                    key.providerId(), key.settlementTarget(), paymentIds.size());
            settlementWriter.settleGroup(key.providerId(), key.settlementTarget(), paymentIds);
        }
    }

    /** Kunci batch: satu nilai {@code settlements.settlement_target} per baris. */
    private record GroupKey(Long providerId, SettlementTarget settlementTarget) {
    }
}
