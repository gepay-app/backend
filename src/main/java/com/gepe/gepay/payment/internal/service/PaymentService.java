package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.payment.api.PaymentApi;
import com.gepe.gepay.payment.api.dtos.ChannelResponse;
import com.gepe.gepay.payment.api.dtos.CreatePaymentCommand;
import com.gepe.gepay.payment.api.dtos.CreatePaymentResult;
import com.gepe.gepay.payment.api.dtos.PaymentAttemptResponse;
import com.gepe.gepay.payment.api.dtos.PaymentResponse;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.internal.entity.*;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.PayinProvider;
import com.gepe.gepay.payment.internal.provider.dtos.ChargeRequest;
import com.gepe.gepay.payment.internal.provider.dtos.ChargeResult;
import com.gepe.gepay.payment.internal.repository.*;
import com.gepe.gepay.platform.exception.ServiceException;
import com.gepe.gepay.platform.web.response.CursorPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orkestrator payin. Sengaja <strong>TIDAK</strong> {@code @Transactional}:
 * panggilan API provider adalah remote I/O dan harus berada di luar transaksi.
 * Penulisan DB didelegasikan ke {@link PaymentWriter} (batas transaksi).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService implements PaymentApi {

    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUser currentUser;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ChannelRepository channelRepository;
    private final ChannelRouteRepository channelRouteRepository;
    private final ProviderRepository providerRepository;
    private final FeeResolver feeResolver;
    private final PaymentWriter paymentWriter;
    private final List<PayinProvider> payinProviders;

    @Override
    public CreatePaymentResult createPayment(CreatePaymentCommand command) {
        // 1. Idempotency check
        var existingOpt = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existingOpt.isPresent()) {
            return replay(existingOpt.get());
        }

        // 2. Resolve Channel (harus ada & aktif)
        Channel channel = channelRepository.findByCode(command.channelCode())
                .filter(Channel::getIsActive)
                .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_NOT_FOUND, command.channelCode()));

        // 3. Resolve Route (pemenang by priority; amount tidak ikut memilih)
        ChannelRoute route = channelRouteRepository.findActiveRoute(channel.getId())
                .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_ROUTE_NOT_FOUND, command.channelCode()));

        // 4. Validasi nominal terhadap batas route (maxAmount = 0 berarti tanpa batas).
        //    Dibedakan bawah/atas supaya klien tahu batas yang dilanggar.
        if (command.amount() < route.getMinAmount()) {
            throw new ServiceException(PaymentError.AMOUNT_BELOW_MIN, route.getMinAmount(), command.channelCode());
        }
        if (route.getMaxAmount() > 0 && command.amount() > route.getMaxAmount()) {
            throw new ServiceException(PaymentError.AMOUNT_ABOVE_MAX, route.getMaxAmount(), command.channelCode());
        }

        // 5. Resolve Provider (harus ada & aktif untuk payin) — misconfig server,
        //    jangan bocorkan detail ke klien.
        Provider provider = providerRepository.findById(route.getProviderId())
                .filter(Provider::getIsActive)
                .filter(Provider::getSupportsPayin)
                .orElseThrow(() -> {
                    log.error("Active payin provider {} referenced by route {} is missing or disabled",
                            route.getProviderId(), route.getId());
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        // 6. Resolve adapter provider
        PayinProvider providerClient = payinProviders.stream()
                .filter(p -> p.code().equalsIgnoreCase(provider.getCode()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No PayinProvider adapter registered for provider code {}", provider.getCode());
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        // 7. Resolve & hitung fee snapshot
        FeeResolver.FeeSnapshot snapshot = feeResolver.resolveAndCalculate(
                command.type(),
                command.userId(),
                provider.getId(),
                channel.getId(),
                command.amount()
        );

        // 8. Susun Payment entity
        Payment payment = Payment.create(
                command.idempotencyKey(),
                command.type(),
                command.userId(),
                command.payerId(),
                provider.getId(),
                channel.getId(),
                route.getId(),
                command.amount()
        );
        payment.applyFeeSnapshot(
                snapshot.gatewayFeeConfigId(),
                snapshot.pgFixedFeeAmount(),
                snapshot.pgPercentageFeeBps(),
                snapshot.pgPercentageFeeAmount(),
                snapshot.pgVatBps(),
                snapshot.pgVatAmount(),
                snapshot.pgFeeAmount(),
                snapshot.platformFeeConfigId(),
                snapshot.userFeeOverrideId(),
                snapshot.platformFixedFeeAmount(),
                snapshot.platformPercentageFeeBps(),
                snapshot.platformPercentageFeeAmount(),
                snapshot.platformVatBps(),
                snapshot.platformVatAmount(),
                snapshot.platformFeeAmount(),
                snapshot.totalChargedAmount(),
                snapshot.netCreatorAmount(),
                snapshot.expectedSettlementAmount()
        );
        payment.applyMetadata(command.metadata());

        // 9. TX #1 — simpan Payment + PaymentAttempt(INITIATED).
        //    Unique idempotency_key tetap jadi penjaga idempotensi konkuren.
        PaymentAttempt attempt;
        try {
            attempt = paymentWriter.insertInitiated(payment, route.getId());
        } catch (DataIntegrityViolationException e) {
            Payment existing = paymentRepository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new ServiceException(PaymentError.DUPLICATE_IDEMPOTENCY_KEY, command.idempotencyKey()));
            return replay(existing);
        }

        // 10. Charge provider — DI LUAR transaksi (remote I/O).
        ChargeRequest chargeRequest = new ChargeRequest(
                attempt.getId().toString(),
                snapshot.totalChargedAmount(),
                route.getProviderChannelCode(),
                channel.getType(),
                command.customerName(),
                command.customerEmail(),
                null,
                command.metadata()
        );

        ChargeResult chargeResult;
        try {
            chargeResult = providerClient.createCharge(chargeRequest);
        } catch (RuntimeException e) {
            log.error("Provider charge failed for paymentId={}, attemptId={}", payment.getId(), attempt.getId(), e);
            paymentWriter.markChargeFailed(payment.getId(), attempt.getId());
            throw e;
        }

        // 11. TX #2 — simpan hasil charge & ubah status jadi PENDING.
        PaymentWriter.ChargeOutcome outcome = paymentWriter.applyChargeResult(
                payment.getId(), attempt.getId(), chargeResult);
        return new CreatePaymentResult(
                mapToPaymentResponse(outcome.payment()),
                mapToAttemptResponse(outcome.attempt())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ServiceException(PaymentError.PAYMENT_NOT_FOUND, paymentId));
        return mapToPaymentResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<PaymentResponse> listPayments(UUID cursor, int size) {
        int limit = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        UUID userId = currentUser.userId();
        List<Payment> rows = (cursor == null)
                ? paymentRepository.findByUserIdOrderByIdDesc(userId, Limit.of(limit + 1))
                : paymentRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, Limit.of(limit + 1));
        return CursorPage.of(rows, limit, p -> p.getId().toString())
                .map(this::mapToPaymentResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelResponse> listChannels(ChannelDirection direction) {
        return channelRepository.findByDirectionAndIsActiveTrue(direction)
                .stream().map(this::mapToChannelResponse).toList();
    }

    private CreatePaymentResult replay(Payment existing) {
        log.info("Idempotent payment creation replay: id={}, key={}", existing.getId(), existing.getIdempotencyKey());
        PaymentAttempt latestAttempt = paymentAttemptRepository.findFirstByPaymentIdOrderByCreatedAtDesc(existing.getId())
                .orElseThrow(() -> {
                    log.error("Idempotent replay: payment {} has no attempt", existing.getId());
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });
        return new CreatePaymentResult(mapToPaymentResponse(existing), mapToAttemptResponse(latestAttempt));
    }

    private PaymentResponse mapToPaymentResponse(Payment p) {
        return new PaymentResponse(
                p.getId(),
                p.getIdempotencyKey(),
                p.getType(),
                p.getStatus(),
                p.getUserId(),
                p.getPayerId(),
                p.getProviderId(),
                p.getChannelId(),
                p.getChannelRouteId(),
                p.getGrossAmount(),
                p.getPgFeeAmount(),
                p.getPlatformFeeAmount(),
                p.getTotalChargedAmount(),
                p.getNetCreatorAmount(),
                p.getCreatedAt(),
                p.getPaidAt()
        );
    }

    private PaymentAttemptResponse mapToAttemptResponse(PaymentAttempt a) {
        return new PaymentAttemptResponse(
                a.getId(),
                a.getPaymentId(),
                a.getChannelRouteId(),
                a.getProviderReferenceId(),
                a.getPaymentReferenceNumber(),
                a.getStatus(),
                a.getExpiresAt(),
                a.getCreatedAt()
        );
    }

    private ChannelResponse mapToChannelResponse(Channel c) {
        return new ChannelResponse(
                c.getId(),
                c.getCode(),
                c.getDisplayName(),
                c.getType(),
                c.getDirection()
        );
    }
}
