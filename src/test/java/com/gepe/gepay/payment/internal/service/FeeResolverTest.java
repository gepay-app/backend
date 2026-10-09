package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.payment.api.enums.FeeType;
import com.gepe.gepay.payment.internal.entity.FeeConfig;
import com.gepe.gepay.payment.internal.repository.FeeConfigRepository;
import com.gepe.gepay.payment.internal.repository.UserFeeOverrideRepository;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.platform.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeeResolverTest {

    @Mock
    private FeeConfigRepository feeConfigRepository;

    @Mock
    private UserFeeOverrideRepository userFeeOverrideRepository;

    @InjectMocks
    private FeeResolver feeResolver;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void resolveAndCalculate_StandardConfig_CalculatesCorrectly() {
        FeeConfig gatewayConfig = FeeConfig.create(FeeType.GATEWAY_PROCESSING, null, 1L, 1L, 4000L, 0, 1100, Instant.now(), "note", "admin");
        FeeConfig platformConfig = FeeConfig.create(FeeType.PLATFORM_PAYIN, "DONATION", null, null, 0L, 600, 1100, Instant.now(), "note", "admin");

        when(feeConfigRepository.findActiveConfig(eq(FeeType.GATEWAY_PROCESSING), any(), eq(1L), eq(1L), any()))
                .thenReturn(Optional.of(gatewayConfig));
        when(feeConfigRepository.findActiveConfig(eq(FeeType.PLATFORM_PAYIN), eq("DONATION"), any(), any(), any()))
                .thenReturn(Optional.of(platformConfig));
        when(userFeeOverrideRepository.findActiveOverride(eq(userId), eq(FeeType.PLATFORM_PAYIN), eq("DONATION"), any()))
                .thenReturn(Optional.empty());

        FeeResolver.FeeSnapshot snapshot = feeResolver.resolveAndCalculate("DONATION", userId, 1L, 1L, 100_000L);

        // PG fee = 4000 + 0 + 11% VAT of 4000 (440) = 4440
        assertThat(snapshot.pgFeeAmount()).isEqualTo(4440L);
        // Platform fee = 0 + 6% of 100_000 (6000) + 11% VAT of 6000 (660) = 6660
        assertThat(snapshot.platformFeeAmount()).isEqualTo(6660L);
        // Total charged = 100_000 + 4440 = 104440
        assertThat(snapshot.totalChargedAmount()).isEqualTo(104_440L);
        // Net creator = 100_000 - 6660 = 93340
        assertThat(snapshot.netCreatorAmount()).isEqualTo(93_340L);
    }

    @Test
    void resolveAndCalculate_MissingGatewayConfig_ThrowsServiceException() {
        when(feeConfigRepository.findActiveConfig(eq(FeeType.GATEWAY_PROCESSING), any(), eq(1L), eq(1L), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> feeResolver.resolveAndCalculate("DONATION", userId, 1L, 1L, 100_000L))
                .isInstanceOf(ServiceException.class)
                .hasMessage(PaymentError.INTERNAL_ERROR.getMessageKey());
    }

    @Test
    void resolveAndCalculate_MissingPlatformConfig_ThrowsServiceException() {
        FeeConfig gatewayConfig = FeeConfig.create(FeeType.GATEWAY_PROCESSING, null, 1L, 1L, 4000L, 0, 1100, Instant.now(), "note", "admin");
        when(feeConfigRepository.findActiveConfig(eq(FeeType.GATEWAY_PROCESSING), any(), eq(1L), eq(1L), any()))
                .thenReturn(Optional.of(gatewayConfig));
        when(feeConfigRepository.findActiveConfig(eq(FeeType.PLATFORM_PAYIN), eq("UNKNOWN"), any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> feeResolver.resolveAndCalculate("UNKNOWN", userId, 1L, 1L, 100_000L))
                .isInstanceOf(ServiceException.class)
                .hasMessage(PaymentError.INTERNAL_ERROR.getMessageKey());
    }
}
