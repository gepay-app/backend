package com.gepe.gepay.donation.internal.service;

import com.gepe.gepay.donation.internal.dto.DonationPaidResult;
import com.gepe.gepay.donation.internal.entity.Donation;
import com.gepe.gepay.donation.internal.entity.DonationStatus;
import com.gepe.gepay.donation.internal.entity.DonationType;
import com.gepe.gepay.donation.internal.repository.DonationRepository;
import com.gepe.gepay.donation.internal.util.OverlayDurationCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationPaidServiceTest {

    @Mock
    private DonationRepository donationRepository;
    @Mock
    private OverlayWriter overlayWriter;
    @Mock
    private OverlayDurationCalculator durationCalculator;

    private DonationPaidService service;

    @BeforeEach
    void setUp() {
        service = new DonationPaidService(donationRepository, overlayWriter, durationCalculator);
    }

    @Test
    void marksDonationPaidAndEnqueuesOverlay() {
        UUID creatorId = UUID.randomUUID();
        Donation donation = Donation.create(UUID.randomUUID(), creatorId, "idem", "VA_BCA",
                "Budi", null, 50_000L, DonationType.TEXT, "halo", null, false);
        UUID paymentId = UUID.randomUUID();
        when(donationRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(donation));
        when(durationCalculator.durationSeconds(eq(DonationType.TEXT), anyLong(), anyInt())).thenReturn(100);

        Optional<DonationPaidResult> result = service.handlePaid(paymentId, Instant.now());

        assertThat(result).isPresent();
        assertThat(result.get().creatorId()).isEqualTo(creatorId);
        assertThat(result.get().donationId()).isEqualTo(donation.getId());
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.PAID);
        assertThat(donation.getDurationSeconds()).isEqualTo(100);
        verify(overlayWriter).createIfAbsent(donation, 100);
    }

    @Test
    void skipsWhenAlreadyPaid() {
        UUID creatorId = UUID.randomUUID();
        Donation donation = Donation.create(UUID.randomUUID(), creatorId, "idem", "VA_BCA",
                "Budi", null, 50_000L, DonationType.TEXT, "halo", null, false);
        donation.markPaid(Instant.now(), 30);
        UUID paymentId = UUID.randomUUID();
        when(donationRepository.findByPaymentId(paymentId)).thenReturn(Optional.of(donation));

        Optional<DonationPaidResult> result = service.handlePaid(paymentId, Instant.now());

        assertThat(result).isEmpty();
        verifyNoInteractions(overlayWriter, durationCalculator);
    }

    @Test
    void ignoresPaymentWithoutDonation() {
        UUID paymentId = UUID.randomUUID();
        when(donationRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());

        Optional<DonationPaidResult> result = service.handlePaid(paymentId, Instant.now());

        assertThat(result).isEmpty();
        verifyNoInteractions(overlayWriter, durationCalculator);
    }
}
