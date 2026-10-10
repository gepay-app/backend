package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.WithdrawalStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Getter

@Entity
@Table(name = "withdrawals", schema = "payment")
public class Withdrawal {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 160)
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", insertable = false, updatable = false)
    private PayoutDestination destination;

    @NotNull
    @Column(name = "channel_route_id", nullable = false)
    private Long channelRouteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_route_id", insertable = false, updatable = false)
    private ChannelRoute channelRoute;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private WithdrawalStatus status;

    @NotNull
    @Column(name = "requested_amount", nullable = false)
    private Long requestedAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "withdrawal_fee_amount", nullable = false)
    private Long withdrawalFeeAmount;

    @NotNull
    @Column(name = "net_disbursement_amount", nullable = false)
    private Long netDisbursementAmount;

    @Size(max = 40)
    @NotNull
    @Column(name = "destination_account_number", nullable = false, length = 40)
    private String destinationAccountNumber;

    @Size(max = 120)
    @NotNull
    @Column(name = "destination_account_name", nullable = false, length = 120)
    private String destinationAccountName;

    @Size(max = 20)
    @Column(name = "destination_bank_code", length = 20)
    private String destinationBankCode;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Permintaan tarik dana baru. Snapshot tujuan diambil dari
     * {@code PayoutDestination} oleh pemanggil supaya data yang tercatat tidak
     * ikut berubah bila rekening diedit nanti.
     */
    public static Withdrawal create(
            String idempotencyKey,
            UUID userId,
            UUID destinationId,
            Long channelRouteId,
            Long requestedAmount,
            String destinationAccountNumber,
            String destinationAccountName,
            String destinationBankCode
    ){
        Withdrawal w = new Withdrawal();
        w.id = UuidCreator.getTimeOrderedEpoch();
        w.idempotencyKey = idempotencyKey;
        w.userId = userId;
        w.destinationId = destinationId;
        w.channelRouteId = channelRouteId;
        w.status = WithdrawalStatus.REQUESTED;
        w.requestedAmount = requestedAmount;
        w.destinationAccountNumber = destinationAccountNumber;
        w.destinationAccountName = destinationAccountName;
        w.destinationBankCode = destinationBankCode;
        w.createdAt = Instant.now();
        w.updatedAt = w.createdAt;
        return w;
    }

    /**
     * Snapshot fee penarikan + net yang sampai ke rekening creator. Dipanggil
     * setelah resolve {@code PLATFORM_WITHDRAWAL} fee supaya angka bisa diaudit.
     */
    public void applyFeeSnapshot(long withdrawalFeeAmount, long netDisbursementAmount) {
        this.withdrawalFeeAmount = withdrawalFeeAmount;
        this.netDisbursementAmount = netDisbursementAmount;
        this.updatedAt = Instant.now();
    }

    public void markProcessing() {
        this.status = WithdrawalStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void markPaid(Instant at) {
        this.status = WithdrawalStatus.PAID;
        this.completedAt = at;
        this.updatedAt = Instant.now();
    }

    public void markFailed(Instant at) {
        this.status = WithdrawalStatus.FAILED;
        this.completedAt = at;
        this.updatedAt = Instant.now();
    }
}