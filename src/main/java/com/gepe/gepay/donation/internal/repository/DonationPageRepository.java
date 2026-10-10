package com.gepe.gepay.donation.internal.repository;

import com.gepe.gepay.donation.internal.entity.DonationPage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DonationPageRepository extends JpaRepository<DonationPage, UUID> {

    Optional<DonationPage> findByCreatorId(UUID creatorId);

    Optional<DonationPage> findBySlug(String slug);

    Optional<DonationPage> findByOverlayKey(String overlayKey);

    boolean existsBySlug(String slug);

    boolean existsByOverlayKey(String overlayKey);
}
