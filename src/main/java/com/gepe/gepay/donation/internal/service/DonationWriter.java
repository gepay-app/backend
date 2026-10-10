package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.DonationRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Batas transaksi penulisan donasi. Dipisah dari {@link DonationService}
 * (orkestrator) supaya panggilan remote ke payment berada di luar transaksi
 * (AGENTS.md §3 transactions).
 */
@Service
@RequiredArgsConstructor
public class DonationWriter {

    private final DonationRepository donationRepository;

    @Transactional
    public Donation insertPending(Donation donation) {
        return donationRepository.saveAndFlush(donation);
    }

    @Transactional
    public Donation attachPayment(
            UUID donationId,
            UUID paymentId,
            String referenceNumber,
            Instant expiresAt,
            Long totalChargedAmount
    ) {
        Donation donation = donationRepository.findById(donationId)
                .orElseThrow(() -> new ServiceException(DonationError.DONATION_NOT_FOUND, donationId));
        donation.attachPayment(paymentId, referenceNumber, expiresAt, totalChargedAmount);
        return donationRepository.saveAndFlush(donation);
    }
}
