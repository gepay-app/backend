package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.config.DonationOverlayProperties;
import com.gepe.gepay.donation.internal.dto.CreateDonationCommand;
import com.gepe.gepay.donation.internal.dto.DonationResponse;
import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.entity.DonationPage;
import com.gepe.gepay.donation.internal.entity.DonationType;
import com.gepe.gepay.donation.internal.repository.DonationRepository;
import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.payment.api.dtos.PaymentAttemptResponse;
import com.gepe.gepay.payment.api.dtos.PaymentResponse;
import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.platform.exception.ValidationException;
import com.gepe.gepay.platform.i18n.MessageHelper;
import com.gepe.gepay.platform.web.response.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationServiceTest {

    @Mock
    private DonationRepository donationRepository;
    @Mock
    private DonationPageService donationPageService;
    @Mock
    private DonationWriter donationWriter;
    @Mock
    private PaymentApi paymentApi;
    @Mock
    private MessageHelper messageHelper;
    @Mock
    private CurrentUser currentUser;

    private DonationService donationService;

    @BeforeEach
    void setUp() {
        donationService = new DonationService(
                donationRepository,
                donationPageService,
                donationWriter,
                paymentApi,
                new DonationOverlayProperties(),
                messageHelper,
                currentUser);
    }

    @Test
    void rejectsTextWithoutMessage() {
        assertValidationField(command("TEXT", null, null), "message");
    }

    @Test
    void rejectsTextWithVideoUrl() {
        assertValidationField(command("TEXT", "halo", "https://youtu.be/dQw4w9WgXcQ"), "videoUrl");
    }

    @Test
    void rejectsYoutubeWithoutVideoUrl() {
        assertValidationField(command("YOUTUBE", null, null), "videoUrl");
    }

    @Test
    void rejectsNonYoutubeUrl() {
        assertValidationField(command("YOUTUBE", null, "https://vimeo.com/dQw4w9WgXcQ"), "videoUrl");
    }

    @Test
    void rejectsUnknownType() {
        assertValidationField(command("WHATEVER", "hi", null), "type");
    }

    @Test
    void rejectsOversizedMessage() {
        assertValidationField(command("TEXT", "x".repeat(351), null), "message");
    }

    @Test
    void createsPendingDonationAndCallsPaymentWithDonationTarget() {
        UUID creatorId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        DonationPage page = DonationPage.create(creatorId, "OVERLAY-KEY", "creator-slug");

        when(donationRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(donationPageService.requireByCreatorId(creatorId)).thenReturn(page);
        when(paymentApi.createPayment(any())).thenReturn(paymentResult(creatorId, paymentId));

        AtomicReference<Donation> draftRef = new AtomicReference<>();
        when(donationWriter.insertPending(any(Donation.class))).thenAnswer(inv -> {
            Donation draft = inv.getArgument(0);
            draftRef.set(draft);
            return draft;
        });
        when(donationWriter.attachPayment(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Donation draft = draftRef.get();
            draft.attachPayment(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), inv.getArgument(4));
            return draft;
        });

        DonationResponse response = donationService.createDonation(new CreateDonationCommand(
                "idem-1", creatorId, 50_000L, "Budi", "budi@example.com", "VA_BCA", "TEXT", "halo", null, false));

        assertThat(response.donationId()).isEqualTo(draftRef.get().getId());
        assertThat(response.paymentId()).isEqualTo(paymentId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.paymentReferenceNumber()).isEqualTo("VA-123");

        ArgumentCaptor<CreatePaymentCommand> captor = ArgumentCaptor.forClass(CreatePaymentCommand.class);
        verify(paymentApi).createPayment(captor.capture());
        CreatePaymentCommand paymentCommand = captor.getValue();
        assertThat(paymentCommand.type()).isEqualTo("DONATION");
        assertThat(paymentCommand.userId()).isEqualTo(creatorId);
        assertThat(paymentCommand.payerId()).isNull();
        assertThat(paymentCommand.channelCode()).isEqualTo("VA_BCA");
        assertThat(paymentCommand.amount()).isEqualTo(50_000L);
        assertThat(paymentCommand.idempotencyKey()).isEqualTo("DONATION:" + draftRef.get().getId());
        assertThat(paymentCommand.metadata()).containsEntry("donationId", draftRef.get().getId().toString());
    }

    @Test
    void listMyDonations_MapsCreatorHistory() {
        UUID creatorId = UUID.randomUUID();
        when(currentUser.userId()).thenReturn(creatorId);
        Donation donation = Donation.create(
                UUID.randomUUID(), creatorId, "idem-list", "VA_BCA",
                "Budi", "budi@example.com", 50_000L, DonationType.TEXT, "halo", null, false);
        when(donationRepository.findByCreatorIdOrderByIdDesc(eq(creatorId), any()))
                .thenReturn(List.of(donation));

        var result = donationService.listMyDonations(null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).donorName()).isEqualTo("Budi");
        assertThat(result.items().get(0).message()).isEqualTo("halo");
        assertThat(result.hasNext()).isFalse();
    }

    private void assertValidationField(CreateDonationCommand command, String field) {
        ValidationException ex = catchThrowableOfType(
                () -> donationService.createDonation(command), ValidationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getErrors()).extracting(ValidationError::field).contains(field);
    }

    private CreateDonationCommand command(String type, String message, String videoUrl) {
        return new CreateDonationCommand(
                "idem-1", UUID.randomUUID(), 50_000L, "Budi", "budi@example.com", "VA_BCA",
                type, message, videoUrl, false);
    }

    private CreatePaymentResult paymentResult(UUID creatorId, UUID paymentId) {
        PaymentResponse payment = new PaymentResponse(
                paymentId, "DONATION:x", "DONATION", PaymentStatus.PENDING, creatorId, null,
                1L, 1L, 1L, 50_000L, 0L, 0L, 50_300L, 50_000L, Instant.now(), null);
        PaymentAttemptResponse attempt = new PaymentAttemptResponse(
                UUID.randomUUID(), paymentId, 1L, "ORDER-1", "VA-123",
                PaymentAttemptStatus.PENDING, Instant.now().plusSeconds(3600), Instant.now());
        return new CreatePaymentResult(payment, attempt);
    }
}
