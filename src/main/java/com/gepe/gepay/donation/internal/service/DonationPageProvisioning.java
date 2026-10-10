package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.entity.DonationPage;
import com.gepe.gepay.donation.internal.repository.DonationPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert page di transaksi {@code REQUIRES_NEW} supaya race unique
 * ({@code creator_id} / {@code overlay_key}) bisa ditangani pemanggil tanpa
 * menandai transaksi luar rollback-only (pola sama seperti ledger
 * {@code AccountProvisioningService}).
 */
@Service
@RequiredArgsConstructor
public class DonationPageProvisioning {

    private final DonationPageRepository pageRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DonationPage insert(DonationPage page) {
        return pageRepository.saveAndFlush(page);
    }
}
