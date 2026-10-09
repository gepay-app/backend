package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.api.enums.FeeType;
import com.gepe.gepay.payment.internal.entity.FeeConfig;
import com.gepe.gepay.payment.internal.entity.UserFeeOverride;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.repository.FeeConfigRepository;
import com.gepe.gepay.payment.internal.repository.UserFeeOverrideRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeResolver {

    private final FeeConfigRepository feeConfigRepository;
    private final UserFeeOverrideRepository userFeeOverrideRepository;

    public record FeeSnapshot(
            Long gatewayFeeConfigId,
            Long pgFixedFeeAmount,
            Integer pgPercentageFeeBps,
            Long pgPercentageFeeAmount,
            Integer pgVatBps,
            Long pgVatAmount,
            Long pgFeeAmount,
            Long platformFeeConfigId,
            Long userFeeOverrideId,
            Long platformFixedFeeAmount,
            Integer platformPercentageFeeBps,
            Long platformPercentageFeeAmount,
            Integer platformVatBps,
            Long platformVatAmount,
            Long platformFeeAmount,
            Long totalChargedAmount,
            Long netCreatorAmount,
            Long expectedSettlementAmount
    ) {}

    public FeeSnapshot resolveAndCalculate(
            String productType,
            UUID userId,
            Long providerId,
            Long channelId,
            Long grossAmount
    ) {
        Instant now = Instant.now();

        // 1. Resolve PG (Gateway) fee.
        //    Wajib ada: tanpa config, fee PG diam-diam 0 = undercharge. Misconfig
        //    server -> 500 generik + log.error (jangan bocorkan provider/channel).
        FeeConfig gatewayConfig = feeConfigRepository.findActiveConfig(
                FeeType.GATEWAY_PROCESSING, null, providerId, channelId, now
        ).orElseThrow(() -> {
            log.error("No active GATEWAY_PROCESSING fee config for providerId={}, channelId={}", providerId, channelId);
            return new ServiceException(PaymentError.INTERNAL_ERROR);
        });

        Long gatewayFeeConfigId = gatewayConfig.getId();
        long pgFixedFeeAmount = gatewayConfig.getFixedAmount();
        int pgPercentageFeeBps = gatewayConfig.getPercentageBps();
        int pgVatBps = gatewayConfig.getVatBps();

        long pgPercentageFeeAmount = Math.round((double) grossAmount * pgPercentageFeeBps / 10000.0);
        long pgVatAmount = Math.round((double) (pgFixedFeeAmount + pgPercentageFeeAmount) * pgVatBps / 10000.0);
        long pgFeeAmount = pgFixedFeeAmount + pgPercentageFeeAmount + pgVatAmount;

        // 2. Resolve Platform fee (UserFeeOverride vs FeeConfig)
        Optional<UserFeeOverride> overrideOpt = userFeeOverrideRepository.findActiveOverride(
                userId, FeeType.PLATFORM_PAYIN, productType, now
        );

        Long userFeeOverrideId = null;
        Long platformFeeConfigId = null;
        long platformFixedFeeAmount = 0L;
        int platformPercentageFeeBps = 0;
        int platformVatBps = 0;

        if (overrideOpt.isPresent()) {
            UserFeeOverride override = overrideOpt.get();
            userFeeOverrideId = override.getId();
            platformFixedFeeAmount = override.getFixedAmount();
            platformPercentageFeeBps = override.getPercentageBps();
            platformVatBps = override.getVatBps();
        } else {
            FeeConfig platformConfig = feeConfigRepository.findActiveConfig(
                    FeeType.PLATFORM_PAYIN, productType, null, null, now
            ).orElseThrow(() -> {
                log.error("No active PLATFORM_PAYIN fee config for productType={}", productType);
                return new ServiceException(PaymentError.INTERNAL_ERROR);
            });

            platformFeeConfigId = platformConfig.getId();
            platformFixedFeeAmount = platformConfig.getFixedAmount();
            platformPercentageFeeBps = platformConfig.getPercentageBps();
            platformVatBps = platformConfig.getVatBps();
        }

        long platformPercentageFeeAmount = Math.round((double) grossAmount * platformPercentageFeeBps / 10000.0);
        long platformVatAmount = Math.round((double) (platformFixedFeeAmount + platformPercentageFeeAmount) * platformVatBps / 10000.0);
        long platformFeeAmount = platformFixedFeeAmount + platformPercentageFeeAmount + platformVatAmount;

        // 3. Pass-through PG fee to total charged amount
        long totalChargedAmount = grossAmount + pgFeeAmount;
        long netCreatorAmount = grossAmount - platformFeeAmount;
        long expectedSettlementAmount = grossAmount;

        return new FeeSnapshot(
                gatewayFeeConfigId,
                pgFixedFeeAmount,
                pgPercentageFeeBps,
                pgPercentageFeeAmount,
                pgVatBps,
                pgVatAmount,
                pgFeeAmount,
                platformFeeConfigId,
                userFeeOverrideId,
                platformFixedFeeAmount,
                platformPercentageFeeBps,
                platformPercentageFeeAmount,
                platformVatBps,
                platformVatAmount,
                platformFeeAmount,
                totalChargedAmount,
                netCreatorAmount,
                expectedSettlementAmount
        );
    }
}
