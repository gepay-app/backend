package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.PayoutStatus;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@NoArgsConstructor
@Getter

@Entity
@Table(name = "payouts", schema = "payment")
public class Payout {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "withdrawal_id", nullable = false)
    private UUID withdrawalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "withdrawal_id", insertable = false, updatable = false)
    private Withdrawal withdrawal;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private PayoutStatus status;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "provider_fee_amount", nullable = false)
    private Long providerFeeAmount;

    @Size(max = 120)
    @Column(name = "provider_reference_id", length = 120)
    private String providerReferenceId;

    @Size(max = 60)
    @Column(name = "failure_code", length = 60)
    private String failureCode;

    @Size(max = 240)
    @Column(name = "failure_reason", length = 240)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private Map<String, Object> rawPayload;

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
     * Percobaan disbursement ke provider (1 withdrawal bisa banyak payout saat retry).
     * {@code amount} = nominal yang MASUK ke rekening creator (net),
     * {@code providerFeeAmount} dikurangi dari float provider.
     */
    public static Payout create(
            UUID withdrawalId,
            Long providerId,
            Long amount,
            Long providerFeeAmount,
            String providerReferenceId
    ){
        Payout p = new Payout();
        p.id = UuidCreator.getTimeOrderedEpoch();
        p.withdrawalId = withdrawalId;
        p.providerId = providerId;
        p.status = PayoutStatus.PENDING;
        p.amount = amount;
        p.providerFeeAmount = providerFeeAmount;
        p.providerReferenceId = providerReferenceId;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void recordProviderReference(String providerReferenceId) {
        this.providerReferenceId = providerReferenceId;
        this.updatedAt = Instant.now();
    }

    public void recordRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload;
        this.updatedAt = Instant.now();
    }

    public void markCompleted(Instant at) {
        this.status = PayoutStatus.COMPLETED;
        this.completedAt = at;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String failureCode, String failureReason, Instant at) {
        this.status = PayoutStatus.FAILED;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.completedAt = at;
        this.updatedAt = Instant.now();
    }
}