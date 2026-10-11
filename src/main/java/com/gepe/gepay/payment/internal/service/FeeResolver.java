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

    /**
     * Komponen fee gateway yang ditanggung pembayar (tanpa fee platform).
     * Dipakai katalog PAYIN publik supaya fee platform tidak ikut terekspos.
     */
    public record GatewayFeeSnapshot(
            Long pgFeeAmount,
            Long totalChargedAmount
    ) {}

    /** Tarif penarikan efektif (base config atau override user) + biaya penuh. */
    public record WithdrawalFee(
            Long fixedFee,
            Integer percentageBps,
            Long feeAmount
    ) {}

    private record GatewayFeeBreakdown(
            Long configId,
            long fixedFeeAmount,
            int percentageBps,
            long percentageFeeAmount,
            int vatBps,
            long vatAmount,
            long feeAmount
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
        GatewayFeeBreakdown gateway = gatewayFee(
                requireGatewayConfig(providerId, channelId, now), grossAmount);

        Long gatewayFeeConfigId = gateway.configId();
        long pgFixedFeeAmount = gateway.fixedFeeAmount();
        int pgPercentageFeeBps = gateway.percentageBps();
        int pgVatBps = gateway.vatBps();
        long pgPercentageFeeAmount = gateway.percentageFeeAmount();
        long pgVatAmount = gateway.vatAmount();
        long pgFeeAmount = gateway.feeAmount();

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

    /**
     * Fee gateway saja untuk sebuah provider+channel (tanpa fee platform).
     * Sengaja tidak menyentuh PLATFORM_PAYIN/UserFeeOverride supaya bisa dipakai
     * endpoint publik: hanya komponen yang ditanggung pembayar yang dihitung.
     */
    public GatewayFeeSnapshot resolveGatewayFee(Long providerId, Long channelId, Long grossAmount) {
        GatewayFeeBreakdown gateway = gatewayFee(
                requireGatewayConfig(providerId, channelId, Instant.now()), grossAmount);
        return new GatewayFeeSnapshot(
                gateway.feeAmount(),
                grossAmount + gateway.feeAmount()
        );
    }

    /**
     * Tarif PLATFORM_WITHDRAWAL efektif untuk user: pakai {@code UserFeeOverride}
     * bila ada (mis. VIP), kalau tidak fallback ke {@code fee_configs}. Biaya
     * penuh dihitung dari {@code grossAmount} yang diminta.
     */
    public WithdrawalFee resolveWithdrawalFee(UUID userId, long grossAmount) {
        Instant now = Instant.now();

        long fixedFee;
        int percentageBps;
        Optional<UserFeeOverride> overrideOpt = userFeeOverrideRepository.findActiveOverride(
                userId, FeeType.PLATFORM_WITHDRAWAL, null, now
        );
        if (overrideOpt.isPresent()) {
            UserFeeOverride override = overrideOpt.get();
            fixedFee = override.getFixedAmount();
            percentageBps = override.getPercentageBps();
        } else {
            FeeConfig baseConfig = feeConfigRepository.findActiveConfig(
                            FeeType.PLATFORM_WITHDRAWAL, null, null, null, now)
                    .orElseThrow(() -> {
                        log.error("No active PLATFORM_WITHDRAWAL fee config");
                        return new ServiceException(PaymentError.INTERNAL_ERROR);
                    });
            fixedFee = baseConfig.getFixedAmount();
            percentageBps = baseConfig.getPercentageBps();
        }

        long feeAmount = fixedFee + Math.round((double) grossAmount * percentageBps / 10000.0);
        return new WithdrawalFee(fixedFee, percentageBps, feeAmount);
    }

    private FeeConfig requireGatewayConfig(Long providerId, Long channelId, Instant now) {
        return feeConfigRepository.findActiveConfig(
                FeeType.GATEWAY_PROCESSING, null, providerId, channelId, now
        ).orElseThrow(() -> {
            log.error("No active GATEWAY_PROCESSING fee config for providerId={}, channelId={}", providerId, channelId);
            return new ServiceException(PaymentError.INTERNAL_ERROR);
        });
    }

    private GatewayFeeBreakdown gatewayFee(FeeConfig config, long grossAmount) {
        long fixedFeeAmount = config.getFixedAmount();
        int percentageBps = config.getPercentageBps();
        int vatBps = config.getVatBps();

        long percentageFeeAmount = Math.round((double) grossAmount * percentageBps / 10000.0);
        long vatAmount = Math.round((double) (fixedFeeAmount + percentageFeeAmount) * vatBps / 10000.0);
        long feeAmount = fixedFeeAmount + percentageFeeAmount + vatAmount;

        return new GatewayFeeBreakdown(
                config.getId(),
                fixedFeeAmount,
                percentageBps,
                percentageFeeAmount,
                vatBps,
                vatAmount,
                feeAmount
        );
    }
}
