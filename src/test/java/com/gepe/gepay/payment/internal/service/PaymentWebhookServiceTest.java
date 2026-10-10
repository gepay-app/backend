package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.api.enums.ProcessedEventType;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.api.event.PaymentPaidEvent;
import com.gepe.gepay.payment.internal.entity.*;
import com.gepe.gepay.payment.internal.provider.PayinProvider;
import com.gepe.gepay.payment.internal.provider.dtos.IncomingProviderNotification;
import com.gepe.gepay.payment.internal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private PayinProvider payinProvider;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ChannelRouteRepository channelRouteRepository;
    @Mock
    private BusinessDayCalculator businessDayCalculator;
    @Mock
    private LedgerApi ledgerApi;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PaymentWebhookService paymentWebhookService;

    @BeforeEach
    void setUp() {
        when(payinProvider.code()).thenReturn("MIDTRANS");
        paymentWebhookService = new PaymentWebhookService(
                List.of(payinProvider),
                providerRepository,
                processedEventRepository,
                paymentAttemptRepository,
                paymentRepository,
                channelRouteRepository,
                businessDayCalculator,
                ledgerApi,
                eventPublisher
        );
    }

    @Test
    void processIncomingWebhook_PaidNotification_PostsLedgerJournalAndUpdatesStatus() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findByCode("MIDTRANS")).thenReturn(Optional.of(provider));

        IncomingProviderNotification notification = new IncomingProviderNotification(
                ProcessedEventType.PAYMENT_PAID,
                "ORDER-123",
                PaymentAttemptStatus.PAID,
                100_000L,
                Instant.now(),
                Map.of("status", "settlement")
        );
        when(payinProvider.parseWebhook(anyString(), any())).thenReturn(notification);

        UUID userId = UUID.randomUUID();
        Payment payment = Payment.create("IDEM-1", "DONATION", userId, null, 1L, 1L, 1L, 100_000L);
        payment.applyFeeSnapshot(null, 0L, 0, 0L, 0, 0L, 4440L, null, null, 0L, 600, 6000L, 1100, 660L, 6660L, 104440L, 93340L, 100000L);

        PaymentAttempt attempt = PaymentAttempt.create(payment.getId(), 1L, "ORDER-123", "VA-123", Instant.now());

        when(paymentAttemptRepository.findByProviderReferenceId("ORDER-123")).thenReturn(Optional.of(attempt));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        ChannelRoute route = ChannelRoute.create(1L, 1L, "BCA_VA", 0L, 0L, 100, 3, SettlementTarget.BANK);
        when(channelRouteRepository.findById(payment.getChannelRouteId())).thenReturn(Optional.of(route));
        when(businessDayCalculator.plusBusinessDays(any(LocalDate.class), eq(3))).thenReturn(LocalDate.now().plusDays(3));

        paymentWebhookService.processIncomingWebhook("MIDTRANS", "{}");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PAID);
        assertThat(payment.getExpectedSettlementDate()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerApi, times(1)).postJournal(
                eq("PAYMENT:" + payment.getId() + ":PAID"),
                eq(JournalReferenceType.PAYMENT),
                eq(payment.getId().toString()),
                anyString(),
                any(),
                linesCaptor.capture(),
                isNull()
        );

        List<JournalLine> lines = linesCaptor.getValue();
        long debit = lines.stream().filter(l -> l.direction() == EntryDirection.DEBIT)
                .mapToLong(JournalLine::amount).sum();
        long credit = lines.stream().filter(l -> l.direction() == EntryDirection.CREDIT)
                .mapToLong(JournalLine::amount).sum();
        assertThat(debit).isEqualTo(credit).isEqualTo(100_000L);

        Map<AccountCode, Long> byAccount = lines.stream()
                .collect(Collectors.toMap(JournalLine::accountCode, JournalLine::amount));
        assertThat(byAccount.get(AccountCode.PG_CLEARING_RECEIVABLE)).isEqualTo(100_000L);
        assertThat(byAccount.get(AccountCode.CREATOR_PAYABLE_PENDING)).isEqualTo(93_340L);
        assertThat(byAccount.get(AccountCode.PLATFORM_FEE_REVENUE)).isEqualTo(6_000L);
        assertThat(byAccount.get(AccountCode.VAT_PAYABLE)).isEqualTo(660L);

        ArgumentCaptor<PaymentPaidEvent> eventCaptor = ArgumentCaptor.forClass(PaymentPaidEvent.class);
        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
        PaymentPaidEvent event = eventCaptor.getValue();
        assertThat(event.paymentId()).isEqualTo(payment.getId());
        assertThat(event.type()).isEqualTo("DONATION");
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.grossAmount()).isEqualTo(100_000L);
        assertThat(event.netCreatorAmount()).isEqualTo(93_340L);
        assertThat(event.paidAt()).isEqualTo(payment.getPaidAt());
    }

    @Test
    void processIncomingWebhook_DuplicateEvent_IdempotentSkip() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findByCode("MIDTRANS")).thenReturn(Optional.of(provider));

        IncomingProviderNotification notification = new IncomingProviderNotification(
                ProcessedEventType.PAYMENT_PAID,
                "ORDER-123",
                PaymentAttemptStatus.PAID,
                100_000L,
                Instant.now(),
                Map.of("status", "settlement")
        );
        when(payinProvider.parseWebhook(anyString(), any())).thenReturn(notification);
        doThrow(new DataIntegrityViolationException("Duplicate key"))
                .when(processedEventRepository).saveAndFlush(any());

        paymentWebhookService.processIncomingWebhook("MIDTRANS", "{}");

        verify(paymentAttemptRepository, never()).findByProviderReferenceId(anyString());
        verify(ledgerApi, never()).postJournal(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processIncomingWebhook_ExpiredNotification_UpdatesStatusWithoutJournal() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findByCode("MIDTRANS")).thenReturn(Optional.of(provider));

        IncomingProviderNotification notification = new IncomingProviderNotification(
                ProcessedEventType.PAYMENT_EXPIRED,
                "ORDER-999",
                PaymentAttemptStatus.EXPIRED,
                100_000L,
                Instant.now(),
                Map.of("status", "expire")
        );
        when(payinProvider.parseWebhook(anyString(), any())).thenReturn(notification);

        UUID userId = UUID.randomUUID();
        Payment payment = Payment.create("IDEM-2", "DONATION", userId, null, 1L, 1L, 1L, 100_000L);
        PaymentAttempt attempt = PaymentAttempt.create(payment.getId(), 1L, "ORDER-999", "VA-999", Instant.now());

        when(paymentAttemptRepository.findByProviderReferenceId("ORDER-999")).thenReturn(Optional.of(attempt));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentAttemptRepository.saveAndFlush(any(PaymentAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentWebhookService.processIncomingWebhook("MIDTRANS", "{}");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.EXPIRED);
        verify(ledgerApi, never()).postJournal(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processIncomingWebhook_PaidAfterExpired_UpgradesToPaidAndPostsJournal() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findByCode("MIDTRANS")).thenReturn(Optional.of(provider));

        IncomingProviderNotification notification = new IncomingProviderNotification(
                ProcessedEventType.PAYMENT_PAID,
                "ORDER-888",
                PaymentAttemptStatus.PAID,
                100_000L,
                Instant.now(),
                Map.of("status", "settlement")
        );
        when(payinProvider.parseWebhook(anyString(), any())).thenReturn(notification);

        UUID userId = UUID.randomUUID();
        Payment payment = Payment.create("IDEM-4", "DONATION", userId, null, 1L, 1L, 1L, 100_000L);
        payment.applyFeeSnapshot(null, 0L, 0, 0L, 0, 0L, 4440L, null, null, 0L, 600, 6000L, 1100, 660L, 6660L, 104440L, 93340L, 100000L);
        payment.markExpired(Instant.now());
        PaymentAttempt attempt = PaymentAttempt.create(payment.getId(), 1L, "ORDER-888", "VA-888", Instant.now());
        attempt.markExpired();

        when(paymentAttemptRepository.findByProviderReferenceId("ORDER-888")).thenReturn(Optional.of(attempt));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        ChannelRoute route = ChannelRoute.create(1L, 1L, "BCA_VA", 0L, 0L, 100, 3, SettlementTarget.BANK);
        when(channelRouteRepository.findById(payment.getChannelRouteId())).thenReturn(Optional.of(route));
        when(businessDayCalculator.plusBusinessDays(any(LocalDate.class), eq(3))).thenReturn(LocalDate.now().plusDays(3));

        paymentWebhookService.processIncomingWebhook("MIDTRANS", "{}");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PAID);
        verify(ledgerApi, times(1)).postJournal(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void processIncomingWebhook_PaidAfterFailed_UpgradesToPaidAndPostsJournal() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findByCode("MIDTRANS")).thenReturn(Optional.of(provider));

        IncomingProviderNotification notification = new IncomingProviderNotification(
                ProcessedEventType.PAYMENT_PAID,
                "ORDER-889",
                PaymentAttemptStatus.PAID,
                100_000L,
                Instant.now(),
                Map.of("status", "settlement")
        );
        when(payinProvider.parseWebhook(anyString(), any())).thenReturn(notification);

        UUID userId = UUID.randomUUID();
        Payment payment = Payment.create("IDEM-5", "DONATION", userId, null, 1L, 1L, 1L, 100_000L);
        payment.applyFeeSnapshot(null, 0L, 0, 0L, 0, 0L, 4440L, null, null, 0L, 600, 6000L, 1100, 660L, 6660L, 104440L, 93340L, 100000L);
        payment.markFailed();
        PaymentAttempt attempt = PaymentAttempt.create(payment.getId(), 1L, "ORDER-889", "VA-889", Instant.now());
        attempt.markFailed();

        when(paymentAttemptRepository.findByProviderReferenceId("ORDER-889")).thenReturn(Optional.of(attempt));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        ChannelRoute route = ChannelRoute.create(1L, 1L, "BCA_VA", 0L, 0L, 100, 3, SettlementTarget.BANK);
        when(channelRouteRepository.findById(payment.getChannelRouteId())).thenReturn(Optional.of(route));
        when(businessDayCalculator.plusBusinessDays(any(LocalDate.class), eq(3))).thenReturn(LocalDate.now().plusDays(3));

        paymentWebhookService.processIncomingWebhook("MIDTRANS", "{}");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PAID);
        verify(ledgerApi, times(1)).postJournal(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void processIncomingWebhook_LateEventAfterPaid_IsIgnored() {
        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findByCode("MIDTRANS")).thenReturn(Optional.of(provider));

        IncomingProviderNotification notification = new IncomingProviderNotification(
                ProcessedEventType.PAYMENT_EXPIRED,
                "ORDER-777",
                PaymentAttemptStatus.EXPIRED,
                100_000L,
                Instant.now(),
                Map.of("status", "expire")
        );
        when(payinProvider.parseWebhook(anyString(), any())).thenReturn(notification);

        UUID userId = UUID.randomUUID();
        Payment payment = Payment.create("IDEM-3", "DONATION", userId, null, 1L, 1L, 1L, 100_000L);
        payment.markPaid(Instant.now());
        PaymentAttempt attempt = PaymentAttempt.create(payment.getId(), 1L, "ORDER-777", "VA-777", Instant.now());

        when(paymentAttemptRepository.findByProviderReferenceId("ORDER-777")).thenReturn(Optional.of(attempt));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        paymentWebhookService.processIncomingWebhook("MIDTRANS", "{}");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.INITIATED);
        verify(ledgerApi, never()).postJournal(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
