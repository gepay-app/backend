package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.payment.api.enums.ChannelType;
import com.gepe.gepay.payment.api.enums.PaymentStatus;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.internal.entity.*;
import com.gepe.gepay.payment.internal.provider.PayinProvider;
import com.gepe.gepay.payment.internal.provider.dtos.ChargeResult;
import com.gepe.gepay.payment.internal.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentAttemptRepository paymentAttemptRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelRouteRepository channelRouteRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private FeeResolver feeResolver;
    @Mock
    private PaymentWriter paymentWriter;
    @Mock
    private PayinProvider payinProvider;

    private PaymentService paymentService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        paymentService = new PaymentService(
                paymentRepository,
                paymentAttemptRepository,
                channelRepository,
                channelRouteRepository,
                providerRepository,
                feeResolver,
                paymentWriter,
                List.of(payinProvider)
        );
    }

    @Test
    void createPayment_Success() {
        when(payinProvider.code()).thenReturn("MIDTRANS");

        CreatePaymentCommand command = new CreatePaymentCommand(
                "IDEM-100", "DONATION", userId, null, "BCA_VA", 100_000L, "John Doe", "john@example.com", Map.of()
        );

        when(paymentRepository.findByIdempotencyKey("IDEM-100")).thenReturn(Optional.empty());

        Channel channel = Channel.create("BCA_VA", "BCA Virtual Account", ChannelType.VA, null);
        when(channelRepository.findByCode("BCA_VA")).thenReturn(Optional.of(channel));

        ChannelRoute route = ChannelRoute.create(1L, channel.getId(), "bca_va", 0L, 0L, 100, 3, SettlementTarget.BANK);
        when(channelRouteRepository.findActiveRoute(channel.getId())).thenReturn(Optional.of(route));

        Provider provider = Provider.create("MIDTRANS", "Midtrans PG", true, false, true);
        when(providerRepository.findById(route.getProviderId())).thenReturn(Optional.of(provider));

        FeeResolver.FeeSnapshot snapshot = new FeeResolver.FeeSnapshot(
                null, 4000L, 0, 0L, 1100, 440L, 4440L, null, null, 0L, 600, 6000L, 1100, 660L, 6660L, 104440L, 93340L, 100000L
        );
        when(feeResolver.resolveAndCalculate("DONATION", userId, provider.getId(), channel.getId(), 100_000L)).thenReturn(snapshot);

        Payment[] captured = new Payment[1];
        when(paymentWriter.insertInitiated(any(Payment.class), eq(route.getId()))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            captured[0] = p;
            return PaymentAttempt.initiate(p.getId(), route.getId());
        });

        ChargeResult chargeResult = new ChargeResult("ORDER-500", "MID-TRX-500", "883012345", Instant.now().plusSeconds(3600), Map.of(), Map.of());
        when(payinProvider.createCharge(any())).thenReturn(chargeResult);
        when(paymentWriter.applyChargeResult(any(), any(), eq(chargeResult))).thenAnswer(invocation -> {
            Payment p = captured[0];
            p.markPending();
            PaymentAttempt a = PaymentAttempt.create(
                    p.getId(), route.getId(), "MID-TRX-500", "883012345", Instant.now().plusSeconds(3600));
            a.markPending();
            return new PaymentWriter.ChargeOutcome(p, a);
        });

        CreatePaymentResult result = paymentService.createPayment(command);

        assertThat(result.payment()).isNotNull();
        assertThat(result.payment().idempotencyKey()).isEqualTo("IDEM-100");
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.attempt().providerReferenceId()).isEqualTo("MID-TRX-500");
    }

    @Test
    void createPayment_IdempotentReplay_ReturnsExistingWithoutCharging() {
        CreatePaymentCommand command = new CreatePaymentCommand(
                "IDEM-100", "DONATION", userId, null, "BCA_VA", 100_000L, "John Doe", "john@example.com", Map.of()
        );

        Payment existing = Payment.create("IDEM-100", "DONATION", userId, null, 1L, 1L, 1L, 100_000L);
        PaymentAttempt attempt = PaymentAttempt.create(
                existing.getId(), 1L, "MID-TRX-OLD", "VA-OLD", Instant.now().plusSeconds(600));

        when(paymentRepository.findByIdempotencyKey("IDEM-100")).thenReturn(Optional.of(existing));
        when(paymentAttemptRepository.findFirstByPaymentIdOrderByCreatedAtDesc(existing.getId()))
                .thenReturn(Optional.of(attempt));

        CreatePaymentResult result = paymentService.createPayment(command);

        assertThat(result.payment().idempotencyKey()).isEqualTo("IDEM-100");
        assertThat(result.attempt().providerReferenceId()).isEqualTo("MID-TRX-OLD");
        verify(payinProvider, never()).createCharge(any());
        verify(paymentRepository, never()).saveAndFlush(any());
    }
}
