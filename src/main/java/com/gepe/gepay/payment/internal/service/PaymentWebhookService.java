package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
import com.gepe.gepay.payment.internal.entity.*;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.PayinProvider;
import com.gepe.gepay.payment.internal.provider.dtos.IncomingProviderNotification;
import com.gepe.gepay.payment.internal.repository.*;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private final List<PayinProvider> payinProviders;
    private final ProviderRepository providerRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final ChannelRouteRepository channelRouteRepository;
    private final BusinessDayCalculator businessDayCalculator;
    private final LedgerApi ledgerApi;

    @Transactional
    public void processIncomingWebhook(String providerCode, String rawPayload) {
        PayinProvider provider = payinProviders.stream()
                .filter(p -> p.code().equalsIgnoreCase(providerCode))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No PayinProvider adapter registered for webhook provider code {}", providerCode);
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        IncomingProviderNotification notification = provider.parseWebhook(rawPayload, Map.of());

        Provider providerEntity = providerRepository.findByCode(providerCode)
                .orElseThrow(() -> {
                    log.error("Webhook provider {} is not registered in the database", providerCode);
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        String externalEventId = notification.providerReferenceId() + ":" + notification.type();

        // 1. Cek Idempotensi melalui DB Inbox (processed_events)
        try {
            ProcessedEvent processedEvent = ProcessedEvent.create(
                    providerEntity.getId(),
                    notification.type(),
                    externalEventId,
                    notification.raw()
            );
            processedEventRepository.saveAndFlush(processedEvent);
        } catch (DataIntegrityViolationException e) {
            log.info("Webhook event already processed (idempotent skip): externalEventId={}", externalEventId);
            return;
        }

        // 2. Load PaymentAttempt & Parent Payment
        PaymentAttempt attempt = paymentAttemptRepository.findByProviderReferenceId(notification.providerReferenceId())
                .orElseThrow(() -> new ServiceException(PaymentError.PAYMENT_ATTEMPT_NOT_FOUND, notification.providerReferenceId()));

        Payment payment = paymentRepository.findById(attempt.getPaymentId())
                .orElseThrow(() -> new ServiceException(PaymentError.PAYMENT_NOT_FOUND, attempt.getPaymentId()));

        // Hanya PAID yang final: event telat tidak boleh menurunkannya. Status
        // lain (EXPIRED/FAILED/CANCELLED) masih boleh dinaikkan oleh settlement.
        // Event sudah tercatat di processed_events, jadi tidak akan di-retry.
        if (payment.isTerminal()) {
            log.warn("Ignoring webhook event {} for payment {} already PAID",
                    notification.type(), payment.getId());
            return;
        }

        if (notification.attemptStatus() == PaymentAttemptStatus.PAID) {
            // 3. Hitung expected settlement date (T+n hari kerja)
            ChannelRoute route = channelRouteRepository.findById(payment.getChannelRouteId())
                    .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_ROUTE_NOT_FOUND, payment.getChannelRouteId()));

            LocalDate paidDate = notification.occurredAt() != null
                    ? LocalDate.ofInstant(notification.occurredAt(), ZoneId.of("Asia/Jakarta"))
                    : LocalDate.now(ZoneId.of("Asia/Jakarta"));

            LocalDate expectedSettlementDate = businessDayCalculator.plusBusinessDays(paidDate, route.getSettlementDelayDays());
            payment.scheduleSettlement(expectedSettlementDate);

            // 4. Posting J-1 journal entry ke ledger module
            String journalIdemKey = "PAYMENT:" + payment.getId() + ":PAID";
            List<JournalLine> lines = List.of(
                    new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, providerEntity.getCode(), EntryDirection.DEBIT, payment.getGrossAmount()),
                    new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, payment.getUserId().toString(), EntryDirection.CREDIT, payment.getNetCreatorAmount()),
                    new JournalLine(AccountCode.PLATFORM_FEE_REVENUE, null, EntryDirection.CREDIT, payment.platformFeeExcludingVat()),
                    new JournalLine(AccountCode.VAT_PAYABLE, null, EntryDirection.CREDIT, payment.getPlatformVatAmount())
            );

            ledgerApi.postJournal(
                    journalIdemKey,
                    JournalReferenceType.PAYMENT,
                    payment.getId().toString(),
                    "Payin payment paid: " + payment.getId(),
                    notification.occurredAt(),
                    lines,
                    null
            );

            // 5. Update statuses
            attempt.markPaid();
            payment.markPaid(notification.occurredAt());

            paymentRepository.saveAndFlush(payment);
            paymentAttemptRepository.saveAndFlush(attempt);

            log.info("PaymentAttempt and Payment marked as PAID & J-1 posted: attemptId={}, paymentId={}, expectedSettlementDate={}",
                    attempt.getId(), payment.getId(), expectedSettlementDate);

        } else if (notification.attemptStatus() == PaymentAttemptStatus.EXPIRED) {
            attempt.markExpired();
            payment.markExpired(notification.occurredAt());

            paymentRepository.saveAndFlush(payment);
            paymentAttemptRepository.saveAndFlush(attempt);

            log.info("PaymentAttempt and Payment marked as EXPIRED: attemptId={}", attempt.getId());

        } else if (notification.attemptStatus() == PaymentAttemptStatus.FAILED) {
            attempt.markFailed();
            payment.markFailed();

            paymentRepository.saveAndFlush(payment);
            paymentAttemptRepository.saveAndFlush(attempt);

            log.info("PaymentAttempt and Payment marked as FAILED: attemptId={}", attempt.getId());
        }
    }
}
