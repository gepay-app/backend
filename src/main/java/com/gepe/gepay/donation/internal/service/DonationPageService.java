package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.dto.DonationPageResponse;
import com.gepe.gepay.donation.internal.dto.PublicDonationPageResponse;
import com.gepe.gepay.donation.internal.dto.UpdateDonationPageCommand;
import com.gepe.gepay.donation.internal.entity.DonationPage;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.DonationPageRepository;
import com.gepe.gepay.donation.internal.util.OverlayKeyGenerator;
import com.gepe.gepay.donation.internal.util.SlugGenerator;
import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.platform.exception.GlobalError;
import com.gepe.gepay.platform.exception.ServiceException;
import com.gepe.gepay.platform.exception.ValidationException;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Halaman donasi per creator (auto-provision). Tanpa remote I/O, jadi semua
 * method menulis bisa {@code @Transactional}.
 */
@Service
@RequiredArgsConstructor
public class DonationPageService {

    private static final int KEY_ATTEMPTS = 5;
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,58}[a-z0-9])?$");

    private final DonationPageRepository pageRepository;
    private final DonationPageProvisioning pageProvisioning;
    private final OverlayKeyGenerator keyGenerator;
    private final SlugGenerator slugGenerator;
    private final OverlayBroadcaster overlayBroadcaster;
    private final CurrentUser currentUser;
    private final MessageHelper messageHelper;

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
        String slug = validateSlug(command.slug());
        if (slug != null && !slug.equals(page.getSlug()) && pageRepository.existsBySlug(slug)) {
            throw new ServiceException(DonationError.SLUG_TAKEN);
        }
        page.updateProfile(
                command.displayName(), command.title(), command.description(), command.imageUrl(), slug);
        try {
            pageRepository.saveAndFlush(page);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException(DonationError.SLUG_TAKEN);
        }
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
    public PublicDonationPageResponse getPublicPageBySlug(String slug) {
        DonationPage page = pageRepository.findBySlug(slug)
                .orElseThrow(() -> new ServiceException(DonationError.PAGE_NOT_FOUND));
        return new PublicDonationPageResponse(
                page.getId(),
                page.getCreatorId(),
                page.getSlug(),
                page.getDisplayName(),
                page.getTitle(),
                page.getDescription(),
                page.getImageUrl(),
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
            String slug = uniqueSlug();
            if (pageRepository.existsByOverlayKey(key)) {
                continue;
            }
            try {
                return pageProvisioning.insert(DonationPage.create(creatorId, key, slug));
            } catch (DataIntegrityViolationException e) {
                // Race: creator_id unik atau overlay_key/slug bentrok — bila page sudah
                // dibuat pemanggil lain, pakai itu; kalau bukan, coba lagi.
                DonationPage existing = pageRepository.findByCreatorId(creatorId).orElse(null);
                if (existing != null) {
                    return existing;
                }
            }
        }
        throw new ServiceException(GlobalError.SYSTEM_ERROR);
    }

    private String uniqueSlug() {
        for (int attempt = 0; attempt < KEY_ATTEMPTS; attempt++) {
            String slug = slugGenerator.generate();
            if (!pageRepository.existsBySlug(slug)) {
                return slug;
            }
        }
        throw new ServiceException(GlobalError.SYSTEM_ERROR);
    }

    /** Normalisasi + validasi slug. {@code null} = tidak diubah. */
    private String validateSlug(String raw) {
        String slug = trimToNull(raw);
        if (slug == null) {
            return null;
        }
        slug = slug.toLowerCase(Locale.ROOT);
        if (!SLUG_PATTERN.matcher(slug).matches()) {
            throw new ValidationException(List.of(
                    new ValidationError("slug", messageHelper.get("donation.invalid_slug"))));
        }
        return slug;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                page.getSlug(),
                page.getDisplayName(),
                page.getTitle(),
                page.getDescription(),
                page.getImageUrl(),
                page.isActive(),
                page.getCreatedAt(),
                page.getUpdatedAt());
    }
}
