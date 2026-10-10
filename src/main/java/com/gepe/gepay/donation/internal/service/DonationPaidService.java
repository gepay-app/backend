package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.dto.DonationPaidResult;
import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.entity.DonationStatus;
import com.gepe.gepay.donation.internal.repository.DonationRepository;
import com.gepe.gepay.donation.internal.util.OverlayDurationCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Menangani donasi yang payment-nya lunas: tandai {@code PAID}, hitung durasi,
 * dan masukkan ke antrean overlay. Idempotent (aman untuk at-least-once).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DonationPaidService {

    private final DonationRepository donationRepository;
    private final OverlayWriter overlayWriter;
    private final OverlayDurationCalculator durationCalculator;

    /**
     * @return hasil (donationId + creatorId) yang perlu di-dispatch/notifikasi
     *         (kosong bila bukan donasi, tidak ditemukan, atau sudah {@code PAID}).
     */
    @Transactional
    public Optional<DonationPaidResult> handlePaid(UUID paymentId, Instant paidAt) {
        Donation donation = donationRepository.findByPaymentId(paymentId).orElse(null);
        if (donation == null) {
            log.warn("PaymentPaid received for unknown donation paymentId={}", paymentId);
            return Optional.empty();
        }
        if (donation.getStatus() == DonationStatus.PAID) {
            log.debug("Donation {} already PAID — idempotent skip", donation.getId());
            return Optional.empty();
        }

        int characterCount = donation.getMessage() == null ? 0 : donation.getMessage().length();
        int durationSeconds = durationCalculator.durationSeconds(
                donation.getType(), donation.getAmount(), characterCount);
        Instant effectivePaidAt = paidAt != null ? paidAt : Instant.now();

        donation.markPaid(effectivePaidAt, durationSeconds);
        overlayWriter.createIfAbsent(donation, durationSeconds);

        log.info("Donation {} marked PAID (duration={}s), overlay enqueued", donation.getId(), durationSeconds);
        return Optional.of(new DonationPaidResult(donation.getId(), donation.getCreatorId()));
    }
}
