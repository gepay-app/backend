package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.internal.entity.ChannelRoute;
import com.gepe.gepay.payment.internal.entity.Payment;
import com.gepe.gepay.payment.internal.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SettlementWriter settlementWriter;

    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(paymentRepository, settlementWriter);
    }

    @Test
    void settleDuePayments_GroupsByProviderAndTarget_AndDelegatesPerGroup() {
        UUID user = UUID.randomUUID();
        Payment p1 = paidPayment("IDEM-1", user, 100_000L, 1L, SettlementTarget.BANK);
        Payment p2 = paidPayment("IDEM-2", user, 200_000L, 1L, SettlementTarget.BANK);
        Payment p3 = paidPayment("IDEM-3", user, 50_000L, 2L, SettlementTarget.BANK);
        Payment p4 = paidPayment("IDEM-4", user, 25_000L, 1L, SettlementTarget.PROVIDER_BALANCE);

        when(paymentRepository.findSettlementCandidates(eq(PaymentStatus.PAID), any()))
                .thenReturn(List.of(p1, p2, p3, p4));

        settlementService.settleDuePayments(LocalDate.now());

        verify(settlementWriter).settleGroup(1L, SettlementTarget.BANK, List.of(p1.getId(), p2.getId()));
        verify(settlementWriter).settleGroup(2L, SettlementTarget.BANK, List.of(p3.getId()));
        verify(settlementWriter).settleGroup(1L, SettlementTarget.PROVIDER_BALANCE, List.of(p4.getId()));
        verify(settlementWriter, times(3)).settleGroup(any(), any(), any());
    }

    @Test
    void settleDuePayments_NoCandidates_DoesNothing() {
        when(paymentRepository.findSettlementCandidates(eq(PaymentStatus.PAID), any()))
                .thenReturn(List.of());

        settlementService.settleDuePayments(LocalDate.now());

        verify(settlementWriter, never()).settleGroup(any(), any(), any());
    }

    private Payment paidPayment(String idem, UUID userId, long gross, long providerId, SettlementTarget target) {
        Payment p = Payment.create(idem, "DONATION", userId, null, providerId, 1L, 1L, gross);
        p.applyFeeSnapshot(null, 0L, 0, 0L, 0, 0L, 0L, null, null, 0L, 0, 0L, 0, 0L, 0L, gross, gross, gross);
        p.markPaid(Instant.now());

        // Route di-set manual supaya grouping bisa membaca settlementTarget tanpa JPA.
        ChannelRoute route = ChannelRoute.create(providerId, 1L, "bca", 0L, 0L, 100, 3, target);
        ReflectionTestUtils.setField(p, "channelRoute", route);
        return p;
    }
}
