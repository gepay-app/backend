package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.dto.UpdateDonationPageCommand;
import com.gepe.gepay.donation.internal.entity.DonationPage;
import com.gepe.gepay.donation.internal.exception.DonationError;
import com.gepe.gepay.donation.internal.repository.DonationPageRepository;
import com.gepe.gepay.donation.internal.util.OverlayKeyGenerator;
import com.gepe.gepay.donation.internal.util.SlugGenerator;
import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.platform.exception.ServiceException;
import com.gepe.gepay.platform.exception.ValidationException;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationPageServiceTest {

    @Mock
    private DonationPageRepository pageRepository;
    @Mock
    private DonationPageProvisioning pageProvisioning;
    @Mock
    private OverlayKeyGenerator keyGenerator;
    @Mock
    private SlugGenerator slugGenerator;
    @Mock
    private OverlayBroadcaster overlayBroadcaster;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private MessageHelper messageHelper;

    private DonationPageService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DonationPageService(
                pageRepository, pageProvisioning, keyGenerator, slugGenerator,
                overlayBroadcaster, currentUser, messageHelper);
    }

    @Test
    void updateMyPage_NormalizesSlugAndSaves() {
        when(currentUser.userId()).thenReturn(userId);
        DonationPage page = DonationPage.create(userId, "OVERLAY-KEY", "oldslug");
        when(pageRepository.findByCreatorId(userId)).thenReturn(Optional.of(page));
        when(pageRepository.existsBySlug("new-slug")).thenReturn(false);

        var response = service.updateMyPage(new UpdateDonationPageCommand(
                "GePe", "Support me", "halo", "https://img.example/a.png", "New-Slug"));

        assertThat(response.slug()).isEqualTo("new-slug");
        assertThat(response.displayName()).isEqualTo("GePe");
        assertThat(response.imageUrl()).isEqualTo("https://img.example/a.png");
    }

    @Test
    void updateMyPage_SlugTaken_ThrowsConflict() {
        when(currentUser.userId()).thenReturn(userId);
        DonationPage page = DonationPage.create(userId, "OVERLAY-KEY", "oldslug");
        when(pageRepository.findByCreatorId(userId)).thenReturn(Optional.of(page));
        when(pageRepository.existsBySlug("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.updateMyPage(
                new UpdateDonationPageCommand(null, null, null, null, "taken")))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(DonationError.SLUG_TAKEN);
    }

    @Test
    void updateMyPage_InvalidSlug_ThrowsValidation() {
        when(currentUser.userId()).thenReturn(userId);
        DonationPage page = DonationPage.create(userId, "OVERLAY-KEY", "oldslug");
        when(pageRepository.findByCreatorId(userId)).thenReturn(Optional.of(page));
        when(messageHelper.get("donation.invalid_slug")).thenReturn("bad slug");

        ValidationException ex = catchThrowableOfType(
                () -> service.updateMyPage(
                        new UpdateDonationPageCommand(null, null, null, null, "Bad Slug!")),
                ValidationException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getErrors()).extracting(ValidationError::field).contains("slug");
    }
}
