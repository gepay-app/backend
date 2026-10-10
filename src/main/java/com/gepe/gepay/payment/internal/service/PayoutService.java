package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.api.enums.FeeType;
import com.gepe.gepay.payment.api.enums.WithdrawalStatus;
import com.gepe.gepay.payment.internal.entity.ChannelRoute;
import com.gepe.gepay.payment.internal.entity.FeeConfig;
import com.gepe.gepay.payment.internal.entity.Provider;
import com.gepe.gepay.payment.internal.entity.Withdrawal;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.provider.PayoutProvider;
import com.gepe.gepay.payment.internal.provider.mock.MockPayoutProvider;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementRequest;
import com.gepe.gepay.payment.internal.provider.dtos.DisbursementResult;
import com.gepe.gepay.payment.internal.repository.ChannelRouteRepository;
import com.gepe.gepay.payment.internal.repository.FeeConfigRepository;
import com.gepe.gepay.payment.internal.repository.ProviderRepository;
import com.gepe.gepay.payment.internal.repository.WithdrawalRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orkestrator payout. Sengaja <strong>TIDAK</strong> {@code @Transactional}:
 * panggilan remote ke provider berada di luar transaksi; penulisan DB
 * didelegasikan ke {@link PayoutWriter}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {

    private final WithdrawalRepository withdrawalRepository;
    private final ChannelRouteRepository channelRouteRepository;
    private final ProviderRepository providerRepository;
    private final FeeConfigRepository feeConfigRepository;
    private final PayoutWriter payoutWriter;
    private final List<PayoutProvider> payoutProviders;

    /**
     * Dev shortcut: pakai {@link MockPayoutProvider} alih-alih vendor (mis. saat
     * sandbox Flip bermasalah). Default {@code false}; jangan aktif di produksi.
     */
    @Value("${payment.payout.mock-enabled:false}")
    private boolean mockEnabled;

    public void processPayouts() {
        List<Withdrawal> withdrawals = withdrawalRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(WithdrawalStatus.REQUESTED, WithdrawalStatus.PROCESSING));
        if (withdrawals.isEmpty()) {
            log.info("No withdrawals pending for payout");
            return;
        }

        for (Withdrawal withdrawal : withdrawals) {
            try {
                processOne(withdrawal);
            } catch (Exception e) {
                log.error("Payout processing failed for withdrawal {}", withdrawal.getId(), e);
            }
        }
    }

    private void processOne(Withdrawal withdrawal) {
        ChannelRoute route = channelRouteRepository.findById(withdrawal.getChannelRouteId())
                .orElseThrow(() -> {
                    log.error("Withdrawal {} references missing channel route {}", withdrawal.getId(), withdrawal.getChannelRouteId());
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        Provider provider = providerRepository.findById(route.getProviderId())
                .filter(Provider::getIsActive)
                .filter(Provider::getSupportsPayout)
                .orElseThrow(() -> {
                    log.error("Active payout provider {} referenced by route {} is missing or disabled",
                            route.getProviderId(), route.getId());
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        String adapterCode = mockEnabled ? MockPayoutProvider.CODE : provider.getCode();
        if (mockEnabled) {
            log.warn("Payout mock mode enabled: using MOCK adapter instead of provider {}", provider.getCode());
        }
        PayoutProvider adapter = payoutProviders.stream()
                .filter(p -> p.code().equalsIgnoreCase(adapterCode))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No PayoutProvider adapter registered for provider code {}", adapterCode);
                    return new ServiceException(PaymentError.INTERNAL_ERROR);
                });

        long providerFee = 0L;
        if (withdrawal.getStatus() == WithdrawalStatus.REQUESTED) {
            FeeConfig feeConfig = feeConfigRepository.findActiveConfig(
                            FeeType.PAYOUT, null, provider.getId(), route.getChannelId(), Instant.now())
                    .orElseThrow(() -> {
                        log.error("No active PAYOUT fee config for providerId={}, channelId={}",
                                provider.getId(), route.getChannelId());
                        return new ServiceException(PaymentError.INTERNAL_ERROR);
                    });
            providerFee = feeConfig.getFixedAmount()
                    + Math.round((double) withdrawal.getNetDisbursementAmount() * feeConfig.getPercentageBps() / 10000.0);
        }

        UUID payoutId = payoutWriter.beginPayout(
                withdrawal.getId(), provider.getId(), withdrawal.getNetDisbursementAmount(), providerFee);

        DisbursementResult result = adapter.disburse(new DisbursementRequest(
                "PAYOUT:" + payoutId,
                withdrawal.getNetDisbursementAmount(),
                route.getProviderChannelCode(),
                withdrawal.getDestinationAccountNumber(),
                withdrawal.getDestinationAccountName(),
                "withdrawal gepay " + withdrawal.getId(),
                Map.of()
        ));

        payoutWriter.applyDisburseResult(payoutId, provider.getCode(), result);
    }
}
