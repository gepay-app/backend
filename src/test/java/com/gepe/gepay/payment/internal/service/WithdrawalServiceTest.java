package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.AccountDto;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationCreateCommand;
import com.gepe.gepay.payment.api.dtos.WithdrawalCreateCommand;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.api.enums.ChannelType;
import com.gepe.gepay.payment.api.enums.FeeType;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
import com.gepe.gepay.payment.internal.entity.Channel;
import com.gepe.gepay.payment.internal.entity.ChannelRoute;
import com.gepe.gepay.payment.internal.entity.FeeConfig;
import com.gepe.gepay.payment.internal.entity.PayoutDestination;
import com.gepe.gepay.payment.internal.entity.Withdrawal;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.repository.ChannelRepository;
import com.gepe.gepay.payment.internal.repository.ChannelRouteRepository;
import com.gepe.gepay.payment.internal.repository.FeeConfigRepository;
import com.gepe.gepay.payment.internal.repository.PayoutDestinationRepository;
import com.gepe.gepay.payment.internal.repository.WithdrawalRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WithdrawalServiceTest {

    @Mock
    private CurrentUser currentUser;
    @Mock
    private PayoutDestinationRepository payoutDestinationRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private ChannelRouteRepository channelRouteRepository;
    @Mock
    private FeeConfigRepository feeConfigRepository;
    @Mock
    private WithdrawalRepository withdrawalRepository;
    @Mock
    private LedgerApi ledgerApi;

    private WithdrawalService withdrawalService;

    private final UUID userId = UUID.randomUUID();
    private final UUID destinationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        withdrawalService = new WithdrawalService(
                currentUser,
                payoutDestinationRepository,
                channelRepository,
                channelRouteRepository,
                feeConfigRepository,
                withdrawalRepository,
                ledgerApi
        );
    }

    @Test
    void createWithdrawal_HoldsBalanceAndPostsBalancedJ5() {
        when(currentUser.userId()).thenReturn(userId);
        when(withdrawalRepository.findByIdempotencyKey("WD-1")).thenReturn(Optional.empty());

        PayoutDestination destination = PayoutDestination.create(userId, 10L, "570000002233331", "GePe Dev", "bri");
        when(payoutDestinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));

        Channel channel = Channel.create("BANK_BRI", "BRI", ChannelType.PAYOUT_BANK, ChannelDirection.PAYOUT);
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));

        ChannelRoute route = ChannelRoute.create(2L, 10L, "bri", 10_000L, 50_000_000L, 100, 0, SettlementTarget.PROVIDER_BALANCE);
        when(channelRouteRepository.findActiveRoute(10L)).thenReturn(Optional.of(route));

        FeeConfig feeConfig = FeeConfig.create(FeeType.PLATFORM_WITHDRAWAL, null, null, null, 3_000L, 0, 0, Instant.now(), null, null);
        when(feeConfigRepository.findActiveConfig(eq(FeeType.PLATFORM_WITHDRAWAL), isNull(), isNull(), isNull(), any()))
                .thenReturn(Optional.of(feeConfig));

        AccountDto available = account(AccountCode.CREATOR_PAYABLE_AVAILABLE, 100_000L);
        when(ledgerApi.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_AVAILABLE, userId.toString()))
                .thenReturn(available);

        when(withdrawalRepository.saveAndFlush(any(Withdrawal.class))).thenAnswer(inv -> inv.getArgument(0));

        WithdrawalCreateCommand command = new WithdrawalCreateCommand("WD-1", destinationId, 90_000L);
        var response = withdrawalService.createWithdrawal(command);

        assertThat(response.requestedAmount()).isEqualTo(90_000L);
        assertThat(response.withdrawalFeeAmount()).isEqualTo(3_000L);
        assertThat(response.netDisbursementAmount()).isEqualTo(87_000L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalLine>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerApi).postJournal(
                eq("WITHDRAWAL:" + response.id() + ":HOLD"),
                eq(JournalReferenceType.WITHDRAWAL),
                eq(response.id().toString()),
                anyString(),
                any(),
                linesCaptor.capture(),
                isNull()
        );

        List<JournalLine> lines = linesCaptor.getValue();
        long debit = lines.stream().filter(l -> l.direction() == EntryDirection.DEBIT).mapToLong(JournalLine::amount).sum();
        long credit = lines.stream().filter(l -> l.direction() == EntryDirection.CREDIT).mapToLong(JournalLine::amount).sum();
        assertThat(debit).isEqualTo(credit).isEqualTo(90_000L);
    }

    @Test
    void createWithdrawal_InsufficientBalance_Throws() {
        when(currentUser.userId()).thenReturn(userId);
        when(withdrawalRepository.findByIdempotencyKey("WD-2")).thenReturn(Optional.empty());

        PayoutDestination destination = PayoutDestination.create(userId, 10L, "570000002233331", "GePe Dev", "bri");
        when(payoutDestinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));

        Channel channel = Channel.create("BANK_BRI", "BRI", ChannelType.PAYOUT_BANK, ChannelDirection.PAYOUT);
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));

        ChannelRoute route = ChannelRoute.create(2L, 10L, "bri", 10_000L, 50_000_000L, 100, 0, SettlementTarget.PROVIDER_BALANCE);
        when(channelRouteRepository.findActiveRoute(10L)).thenReturn(Optional.of(route));

        FeeConfig feeConfig = FeeConfig.create(FeeType.PLATFORM_WITHDRAWAL, null, null, null, 3_000L, 0, 0, Instant.now(), null, null);
        when(feeConfigRepository.findActiveConfig(eq(FeeType.PLATFORM_WITHDRAWAL), isNull(), isNull(), isNull(), any()))
                .thenReturn(Optional.of(feeConfig));

        when(ledgerApi.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_AVAILABLE, userId.toString()))
                .thenReturn(account(AccountCode.CREATOR_PAYABLE_AVAILABLE, 50_000L));

        WithdrawalCreateCommand command = new WithdrawalCreateCommand("WD-2", destinationId, 90_000L);

        assertThatThrownBy(() -> withdrawalService.createWithdrawal(command))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(PaymentError.INSUFFICIENT_AVAILABLE_BALANCE);
    }

    @Test
    void createWithdrawal_IdempotentReplay_ReturnsExistingWithoutPosting() {
        Withdrawal existing = Withdrawal.create("WD-3", userId, destinationId, 1L, 90_000L, "570000002233331", "GePe Dev", "bri");
        existing.applyFeeSnapshot(3_000L, 87_000L);
        when(withdrawalRepository.findByIdempotencyKey("WD-3")).thenReturn(Optional.of(existing));

        var response = withdrawalService.createWithdrawal(new WithdrawalCreateCommand("WD-3", destinationId, 90_000L));

        assertThat(response.idempotencyKey()).isEqualTo("WD-3");
        verify(ledgerApi, never()).postJournal(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listWithdrawals_MapsUserHistory() {
        when(currentUser.userId()).thenReturn(userId);
        Withdrawal w = Withdrawal.create("WD-4", userId, destinationId, 1L, 90_000L, "570000002233331", "GePe Dev", "bri");
        w.applyFeeSnapshot(3_000L, 87_000L);
        when(withdrawalRepository.findByUserIdOrderByIdDesc(eq(userId), any()))
                .thenReturn(List.of(w));

        var result = withdrawalService.listWithdrawals(null, 20);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).idempotencyKey()).isEqualTo("WD-4");
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    void setDefaultPayoutDestination_NotOwned_Throws() {        when(currentUser.userId()).thenReturn(userId);
        PayoutDestination other = PayoutDestination.create(UUID.randomUUID(), 10L, "570000002233331", "Someone Else", "bri");
        when(payoutDestinationRepository.findById(destinationId)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> withdrawalService.setDefaultPayoutDestination(destinationId))
                .isInstanceOf(ServiceException.class)
                .extracting(e -> ((ServiceException) e).getErrorCode())
                .isEqualTo(PaymentError.PAYOUT_DESTINATION_NOT_OWNED);
    }

    @Test
    void getWithdrawalConfig_ReturnsRouteLimitsAndFee() {
        when(currentUser.userId()).thenReturn(userId);
        PayoutDestination destination = PayoutDestination.create(userId, 10L, "570000002233331", "GePe Dev", "bri");
        when(payoutDestinationRepository.findById(destinationId)).thenReturn(Optional.of(destination));

        ChannelRoute route = ChannelRoute.create(2L, 10L, "bri", 10_000L, 50_000_000L, 100, 0, SettlementTarget.PROVIDER_BALANCE);
        when(channelRouteRepository.findActiveRoute(10L)).thenReturn(Optional.of(route));

        FeeConfig feeConfig = FeeConfig.create(FeeType.PLATFORM_WITHDRAWAL, null, null, null, 3_000L, 100, 0, Instant.now(), null, null);
        when(feeConfigRepository.findActiveConfig(eq(FeeType.PLATFORM_WITHDRAWAL), isNull(), isNull(), isNull(), any()))
                .thenReturn(Optional.of(feeConfig));

        var config = withdrawalService.getWithdrawalConfig(destinationId);

        assertThat(config.minAmount()).isEqualTo(10_000L);
        assertThat(config.maxAmount()).isEqualTo(50_000_000L);
        assertThat(config.fixedFee()).isEqualTo(3_000L);
        assertThat(config.feePercentageBps()).isEqualTo(100);
        assertThat(config.currency()).isEqualTo("IDR");
    }

    @Test
    void createPayoutDestination_FirstBecomesDefault() {
        when(currentUser.userId()).thenReturn(userId);
        Channel channel = Channel.create("BANK_BRI", "BRI", ChannelType.PAYOUT_BANK, ChannelDirection.PAYOUT);
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        when(payoutDestinationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of());
        when(payoutDestinationRepository.saveAndFlush(any(PayoutDestination.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = withdrawalService.createPayoutDestination(
                new PayoutDestinationCreateCommand(10L, "570000002233331", "GePe Dev", "BRI"));

        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void createPayoutDestination_SecondNotDefault() {
        when(currentUser.userId()).thenReturn(userId);
        Channel channel = Channel.create("BANK_BRI", "BRI", ChannelType.PAYOUT_BANK, ChannelDirection.PAYOUT);
        when(channelRepository.findById(10L)).thenReturn(Optional.of(channel));
        PayoutDestination existing = PayoutDestination.create(userId, 10L, "570000002233331", "GePe Dev", "BRI");
        when(payoutDestinationRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(existing));
        when(payoutDestinationRepository.saveAndFlush(any(PayoutDestination.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = withdrawalService.createPayoutDestination(
                new PayoutDestinationCreateCommand(10L, "1234567890", "GePe Dev 2", "BRI"));

        assertThat(response.isDefault()).isFalse();
    }

    private AccountDto account(AccountCode code, long balance) {
        return new AccountDto(1L, code, code.getDisplayName(), AccountType.LIABILITY,
                EntryDirection.CREDIT, "USER", userId.toString(), "IDR", balance, true);
    }
}
