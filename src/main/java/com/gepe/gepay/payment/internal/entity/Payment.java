package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.PaymentStatus;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@NoArgsConstructor
@Getter

@Entity
@Table(name = "payments", schema = "payment")
public class Payment {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 160)
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    @NotNull
    @Size(max = 20)
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payer_id")
    private UUID payerId;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @NotNull
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", insertable = false, updatable = false)
    private Channel channel;

    @NotNull
    @Column(name = "channel_route_id", nullable = false)
    private Long channelRouteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_route_id", insertable = false, updatable = false)
    private ChannelRoute channelRoute;

    @NotNull
    @Column(name = "gross_amount", nullable = false)
    private Long grossAmount;

    @Column(name = "gateway_fee_config_id")
    private Long gatewayFeeConfigId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gateway_fee_config_id", insertable = false, updatable = false)
    private FeeConfig gatewayFeeConfig;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pg_fixed_fee_amount", nullable = false)
    private Long pgFixedFeeAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pg_percentage_fee_bps", nullable = false)
    private Integer pgPercentageFeeBps;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pg_percentage_fee_amount", nullable = false)
    private Long pgPercentageFeeAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pg_vat_bps", nullable = false)
    private Integer pgVatBps;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pg_vat_amount", nullable = false)
    private Long pgVatAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "pg_fee_amount", nullable = false)
    private Long pgFeeAmount;

    @Column(name = "platform_fee_config_id")
    private Long platformFeeConfigId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_fee_config_id", insertable = false, updatable = false)
    private FeeConfig platformFeeConfig;

    @Column(name = "user_fee_override_id")
    private Long userFeeOverrideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_fee_override_id", insertable = false, updatable = false)
    private UserFeeOverride userFeeOverride;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "platform_fixed_fee_amount", nullable = false)
    private Long platformFixedFeeAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "platform_percentage_fee_bps", nullable = false)
    private Integer platformPercentageFeeBps;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "platform_percentage_fee_amount", nullable = false)
    private Long platformPercentageFeeAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "platform_vat_bps", nullable = false)
    private Integer platformVatBps;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "platform_vat_amount", nullable = false)
    private Long platformVatAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "platform_fee_amount", nullable = false)
    private Long platformFeeAmount;

    @NotNull
    @Column(name = "total_charged_amount", nullable = false)
    private Long totalChargedAmount;

    @NotNull
    @Column(name = "net_creator_amount", nullable = false)
    private Long netCreatorAmount;

    @NotNull
    @Column(name = "expected_settlement_amount", nullable = false)
    private Long expectedSettlementAmount;

    @Column(name = "expected_settlement_date")
    private LocalDate expectedSettlementDate;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Column(name = "settled_at")
    private Instant settledAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Version
    @ColumnDefault("0")
    @Column(name = "version", nullable = false)
    private Long version;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    /**
     * Payment baru. Snapshot fee (kolom {@code *_fee_*} / {@code *_vat_*} /
     * {@code *_charged_amount}) diisi pemanggil setelah resolve rate card supaya
     * angka yang tercatat bisa diaudit — lihat {@code FeeConfig}.
     */
    public static Payment create(
            String idempotencyKey,
            String type,
            UUID userId,
            UUID payerId,
            Long providerId,
            Long channelId,
            Long channelRouteId,
            Long grossAmount
    ){
        Payment p = new Payment();
        p.id = UuidCreator.getTimeOrderedEpoch();
        p.idempotencyKey = idempotencyKey;
        p.type = type;
        p.status = PaymentStatus.INITIATED;
        p.userId = userId;
        p.payerId = payerId;
        p.providerId = providerId;
        p.channelId = channelId;
        p.channelRouteId = channelRouteId;
        p.grossAmount = grossAmount;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void markPending() {
        this.status = PaymentStatus.PENDING;
        this.updatedAt = Instant.now();
    }

    public void markPaid(Instant paidAt) {
        this.status = PaymentStatus.PAID;
        this.paidAt = paidAt;
        this.updatedAt = Instant.now();
    }

    public void markExpired(Instant expiredAt) {
        this.status = PaymentStatus.EXPIRED;
        this.expiredAt = expiredAt;
        this.updatedAt = Instant.now();
    }

    public void markFailed() {
        this.status = PaymentStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void markCancelled(Instant cancelledAt) {
        this.status = PaymentStatus.CANCELLED;
        this.expiredAt = cancelledAt;
        this.updatedAt = Instant.now();
    }

    public void scheduleSettlement(LocalDate expectedSettlementDate) {
        this.expectedSettlementDate = expectedSettlementDate;
        this.updatedAt = Instant.now();
    }

    public void markSettled(UUID settlementId, Instant settledAt) {
        this.settlementId = settlementId;
        this.settledAt = settledAt;
        this.updatedAt = Instant.now();
    }

}