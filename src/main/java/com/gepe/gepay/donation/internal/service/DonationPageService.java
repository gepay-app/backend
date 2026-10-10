package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.dto.DonationPageResponse;
import com.gepe.gepay.donation.internal.dto.PublicDonationPageResponse;
import com.gepe.gepay.donation.internal.dto.UpdateDonationPageCommand;
import com.gepe.gepay.donation.internal.entity.DonationPage;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.DonationPageRepository;
import com.gepe.gepay.donation.internal.util.OverlayKeyGenerator;
import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.platform.exception.GlobalError;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Halaman donasi per creator (auto-provision). Tanpa remote I/O, jadi semua
 * method menulis bisa {@code @Transactional}.
 */
@Service
@RequiredArgsConstructor
public class DonationPageService {

    private static final int KEY_ATTEMPTS = 5;

    private final DonationPageRepository pageRepository;
    private final DonationPageProvisioning pageProvisioning;
    private final OverlayKeyGenerator keyGenerator;
    private final OverlayBroadcaster overlayBroadcaster;
    private final CurrentUser currentUser;

    /** Page milik user yang sedang login, dibuat bila belum ada. */
    @Transactional
    public DonationPageResponse getOrCreateMyPage() {
        UUID creatorId = currentUser.userId();
        DonationPage page = pageRepository.findByCreatorId(creatorId)
                .orElseGet(() -> provision(creatorId));
        return toResponse(page);
    }

    @Transactional
    public DonationPageResponse updateMyPage(UpdateDonationPageCommand command) {
        DonationPage page = requireByCreatorId(currentUser.userId());
        page.updateProfile(command.displayName(), command.title(), command.description());
        return toResponse(page);
    }

    /** Rotasi {@code overlay_key} (key lama langsung tidak valid). */
    @Transactional
    public String rotateOverlayKey() {
        DonationPage page = requireByCreatorId(currentUser.userId());
        page.rotateOverlayKey(uniqueOverlayKey());
        UUID creatorId = page.getCreatorId();
        String newKey = page.getOverlayKey();
        afterCommit(() -> overlayBroadcaster.keyRotated(creatorId, newKey));
        return newKey;
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Transactional(readOnly = true)
    public PublicDonationPageResponse getPublicPage(UUID creatorId) {
        DonationPage page = requireByCreatorId(creatorId);
        return new PublicDonationPageResponse(
                page.getId(),
                page.getCreatorId(),
                page.getDisplayName(),
                page.getTitle(),
                page.getDescription(),
                page.isActive(),
                page.getCreatedAt());
    }

    /** Dipakai modul internal lain (mis. donation saat membuat donasi). */
    @Transactional(readOnly = true)
    public DonationPage requireByCreatorId(UUID creatorId) {
        return pageRepository.findByCreatorId(creatorId)
                .orElseThrow(() -> new ServiceException(DonationError.PAGE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public DonationPage requireByOverlayKey(String overlayKey) {
        return pageRepository.findByOverlayKey(overlayKey)
                .orElseThrow(() -> new ServiceException(DonationError.PAGE_NOT_FOUND));
    }

    /** Resolusi display WebSocket: {@code overlay_key} → {@code creatorId}. */
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findCreatorIdByOverlayKey(String overlayKey) {
        return pageRepository.findByOverlayKey(overlayKey).map(DonationPage::getCreatorId);
    }

    private DonationPage provision(UUID creatorId) {
        for (int attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
            String key = keyGenerator.generate();
            if (pageRepository.existsByOverlayKey(key)) {
                continue;
            }
            try {
                return pageProvisioning.insert(DonationPage.create(creatorId, key));
            } catch (DataIntegrityViolationException e) {
                // Race: creator_id unik atau overlay_key bentrok — bila page sudah
                // dibuat pemanggil lain, pakai itu; kalau bukan, coba key lain.
                DonationPage existing = pageRepository.findByCreatorId(creatorId).orElse(null);
                if (existing != null) {
                    return existing;
                }
            }
        }
        throw new ServiceException(GlobalError.SYSTEM_ERROR);
    }

    private String uniqueOverlayKey() {
        for (int attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
            String key = keyGenerator.generate();
            if (!pageRepository.existsByOverlayKey(key)) {
                return key;
            }
        }
        throw new ServiceException(GlobalError.SYSTEM_ERROR);
    }

    private DonationPageResponse toResponse(DonationPage page) {
        return new DonationPageResponse(
                page.getId(),
                page.getCreatorId(),
                page.getOverlayKey(),
                page.getDisplayName(),
                page.getTitle(),
                page.getDescription(),
                page.isActive(),
                page.getCreatedAt(),
                page.getUpdatedAt());
    }
}
