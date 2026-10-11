package com.gepe.gepay.payment.internal.service;

import com.gepe.gepay.identity.api.CurrentUser;
import com.gepe.gepay.ledger.api.LedgerApi;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.payment.api.WithdrawalApi;
import com.gepe.gepay.payment.api.dtos.PayoutChannelResponse;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationCreateCommand;
import com.gepe.gepay.payment.api.dtos.PayoutDestinationResponse;
import com.gepe.gepay.payment.api.dtos.WithdrawalCreateCommand;
import com.gepe.gepay.payment.api.dtos.WithdrawalConfigResponse;
import com.gepe.gepay.payment.api.dtos.WithdrawalResponse;
import com.gepe.gepay.payment.api.enums.ChannelDirection;
import com.gepe.gepay.payment.internal.entity.Channel;
import com.gepe.gepay.payment.internal.entity.ChannelRoute;
import com.gepe.gepay.payment.internal.entity.PayoutDestination;
import com.gepe.gepay.payment.internal.entity.Withdrawal;
import com.gepe.gepay.payment.internal.exception.PaymentError;
import com.gepe.gepay.payment.internal.repository.ChannelRepository;
import com.gepe.gepay.payment.internal.repository.ChannelRouteRepository;
import com.gepe.gepay.payment.internal.repository.PayoutDestinationRepository;
import com.gepe.gepay.payment.internal.repository.WithdrawalRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import com.gepe.gepay.platform.web.response.CursorPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade pencairan. Kepemilikan (creator) selalu dari {@link CurrentUser};
 * tidak ada remote I/O di sini, jadi aman {@code @Transactional} penuh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService implements WithdrawalApi {

    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUser currentUser;
    private final PayoutDestinationRepository payoutDestinationRepository;
    private final ChannelRepository channelRepository;
    private final ChannelRouteRepository channelRouteRepository;
    private final FeeResolver feeResolver;
    private final WithdrawalRepository withdrawalRepository;
    private final LedgerApi ledgerApi;

    @Override
    @Transactional
    public PayoutDestinationResponse createPayoutDestination(PayoutDestinationCreateCommand command) {
        Channel channel = channelRepository.findById(command.channelId())
                .filter(Channel::getIsActive)
                .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_NOT_FOUND, command.channelId()));

        if (channel.getDirection() != ChannelDirection.PAYOUT) {
            throw new ServiceException(PaymentError.CHANNEL_NOT_PAYOUT, channel.getCode());
        }

        UUID userId = currentUser.userId();
        PayoutDestination destination = PayoutDestination.create(
                userId,
                channel.getId(),
                command.accountNumber(),
                command.accountName(),
                command.bankCode()
        );

        // Rekening pertama langsung jadi default supaya user tidak perlu
        // menandai manual sebelum bisa menarik dana.
        if (payoutDestinationRepository.findByUserIdOrderByCreatedAtDesc(userId).isEmpty()) {
            destination.markDefault();
        }
        return map(payoutDestinationRepository.saveAndFlush(destination));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutDestinationResponse> listPayoutDestinations() {
        return payoutDestinationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.userId())
                .stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public PayoutDestinationResponse setDefaultPayoutDestination(UUID destinationId) {
        PayoutDestination target = ownedDestination(destinationId);

        payoutDestinationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.userId())
                .forEach(PayoutDestination::clearDefault);
        target.markDefault();
        payoutDestinationRepository.saveAndFlush(target);
        return map(target);
    }

    @Override
    @Transactional
    public void deactivatePayoutDestination(UUID destinationId) {
        PayoutDestination destination = ownedDestination(destinationId);
        destination.deactivate();
        payoutDestinationRepository.saveAndFlush(destination);
    }

    @Override
    @Transactional
    public WithdrawalResponse createWithdrawal(WithdrawalCreateCommand command) {
        var existing = withdrawalRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent withdrawal replay: id={}, key={}", existing.get().getId(), command.idempotencyKey());
            return map(existing.get());
        }

        UUID userId = currentUser.userId();
        PayoutDestination destination = ownedDestination(command.destinationId());
        if (!destination.getIsActive()) {
            throw new ServiceException(PaymentError.PAYOUT_DESTINATION_NOT_FOUND, command.destinationId());
        }

        Channel channel = channelRepository.findById(destination.getChannelId())
                .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_NOT_FOUND, destination.getChannelId()));
        ChannelRoute route = channelRouteRepository.findActiveRoute(destination.getChannelId())
                .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_ROUTE_NOT_FOUND, channel.getCode()));

        if (command.requestedAmount() < route.getMinAmount()) {
            throw new ServiceException(PaymentError.AMOUNT_BELOW_MIN, route.getMinAmount(), channel.getCode());
        }
        if (route.getMaxAmount() > 0 && command.requestedAmount() > route.getMaxAmount()) {
            throw new ServiceException(PaymentError.AMOUNT_ABOVE_MAX, route.getMaxAmount(), channel.getCode());
        }

        FeeResolver.WithdrawalFee fee = feeResolver.resolveWithdrawalFee(userId, command.requestedAmount());
        long withdrawalFee = fee.feeAmount();
        long net = command.requestedAmount() - withdrawalFee;

        long available = ledgerApi.getOrCreateAccount(AccountCode.CREATOR_PAYABLE_AVAILABLE, userId.toString()).balance();
        if (available < command.requestedAmount()) {
            throw new ServiceException(PaymentError.INSUFFICIENT_AVAILABLE_BALANCE, available, command.requestedAmount());
        }

        Withdrawal withdrawal = Withdrawal.create(
                command.idempotencyKey(),
                userId,
                destination.getId(),
                route.getId(),
                command.requestedAmount(),
                destination.getAccountNumber(),
                destination.getAccountName(),
                destination.getBankCode()
        );
        withdrawal.applyFeeSnapshot(withdrawalFee, net);

        Withdrawal saved;
        try {
            saved = withdrawalRepository.saveAndFlush(withdrawal);
        } catch (DataIntegrityViolationException e) {
            Withdrawal winner = withdrawalRepository.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> new ServiceException(PaymentError.DUPLICATE_IDEMPOTENCY_KEY, command.idempotencyKey()));
            return map(winner);
        }

        // J-5: hold saldo available -> withdrawal payable.
        List<JournalLine> lines = List.of(
                new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, userId.toString(), EntryDirection.DEBIT, command.requestedAmount()),
                new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, userId.toString(), EntryDirection.CREDIT, command.requestedAmount())
        );
        ledgerApi.postJournal(
                "WITHDRAWAL:" + saved.getId() + ":HOLD",
                JournalReferenceType.WITHDRAWAL,
                saved.getId().toString(),
                "Withdrawal hold for user " + userId,
                saved.getCreatedAt(),
                lines,
                null
        );

        return map(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WithdrawalResponse getWithdrawal(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawalRepository.findById(withdrawalId)
                .orElseThrow(() -> new ServiceException(PaymentError.WITHDRAWAL_NOT_FOUND, withdrawalId));
        return map(withdrawal);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<WithdrawalResponse> listWithdrawals(UUID cursor, int size) {
        int limit = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        UUID userId = currentUser.userId();
        List<Withdrawal> rows = (cursor == null)
                ? withdrawalRepository.findByUserIdOrderByIdDesc(userId, Limit.of(limit + 1))
                : withdrawalRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, Limit.of(limit + 1));
        return CursorPage.of(rows, limit, w -> w.getId().toString())
                .map(this::map);
    }

    @Override
    @Transactional(readOnly = true)
    public WithdrawalConfigResponse getWithdrawalConfig(UUID destinationId) {
        PayoutDestination destination = ownedDestination(destinationId);
        if (!destination.getIsActive()) {
            throw new ServiceException(PaymentError.PAYOUT_DESTINATION_NOT_FOUND, destinationId);
        }

        ChannelRoute route = channelRouteRepository.findActiveRoute(destination.getChannelId())
                .orElseThrow(() -> new ServiceException(PaymentError.CHANNEL_ROUTE_NOT_FOUND, destination.getChannelId()));

        FeeResolver.WithdrawalFee fee = feeResolver.resolveWithdrawalFee(currentUser.userId(), 0L);

        return new WithdrawalConfigResponse(
                route.getMinAmount(),
                route.getMaxAmount(),
                fee.fixedFee(),
                fee.percentageBps(),
                "IDR"
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutChannelResponse> listPayoutChannels() {
        // Tarif penarikan global per user (memperhitungkan override), sama untuk
        // semua channel; resolusi sekali agar tidak query berulang per channel.
        FeeResolver.WithdrawalFee fee = feeResolver.resolveWithdrawalFee(currentUser.userId(), 0L);
        return channelRepository.findByDirectionAndIsActiveTrue(ChannelDirection.PAYOUT)
                .stream()
                .map(channel -> mapToPayoutChannelResponse(channel, fee))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<PayoutChannelResponse> mapToPayoutChannelResponse(
            Channel channel, FeeResolver.WithdrawalFee fee) {
        Optional<ChannelRoute> routeOpt = channelRouteRepository.findActiveRoute(channel.getId());
        if (routeOpt.isEmpty()) {
            log.warn("Skipping PAYOUT channel {} without an active route", channel.getCode());
            return Optional.empty();
        }
        ChannelRoute route = routeOpt.get();
        return Optional.of(new PayoutChannelResponse(
                channel.getId(),
                channel.getCode(),
                channel.getDisplayName(),
                channel.getType(),
                channel.getDirection(),
                route.getMinAmount(),
                route.getMaxAmount(),
                fee.fixedFee(),
                fee.percentageBps(),
                "IDR"
        ));
    }

    /** Destination milik current user; bedakan "tidak ada" vs "bukan miliknya". */
    private PayoutDestination ownedDestination(UUID destinationId) {
        PayoutDestination destination = payoutDestinationRepository.findById(destinationId)
                .orElseThrow(() -> new ServiceException(PaymentError.PAYOUT_DESTINATION_NOT_FOUND, destinationId));
        if (!destination.getUserId().equals(currentUser.userId())) {
            throw new ServiceException(PaymentError.PAYOUT_DESTINATION_NOT_OWNED, destinationId);
        }
        return destination;
    }

    private PayoutDestinationResponse map(PayoutDestination d) {
        return new PayoutDestinationResponse(
                d.getId(),
                d.getChannelId(),
                d.getAccountNumber(),
                d.getAccountName(),
                d.getBankCode(),
                d.getIsDefault(),
                d.getIsActive(),
                d.getCreatedAt()
        );
    }

    private WithdrawalResponse map(Withdrawal w) {
        return new WithdrawalResponse(
                w.getId(),
                w.getIdempotencyKey(),
                w.getUserId(),
                w.getDestinationId(),
                w.getChannelRouteId(),
                w.getStatus(),
                w.getRequestedAmount(),
                w.getWithdrawalFeeAmount(),
                w.getNetDisbursementAmount(),
                w.getDestinationAccountNumber(),
                w.getDestinationAccountName(),
                w.getDestinationBankCode(),
                w.getCreatedAt(),
                w.getCompletedAt()
        );
    }
}
