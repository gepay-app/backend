package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.FeeType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@NoArgsConstructor
@Getter

@Entity
@Table(name = "fee_configs", schema = "payment")
public class FeeConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "fee_type", nullable = false, length = 30)
    private FeeType feeType;

    @Column(name = "provider_id")
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @Column(name = "channel_id")
    private Long channelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", insertable = false, updatable = false)
    private Channel channel;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "fixed_amount", nullable = false)
    private Long fixedAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "percentage_bps", nullable = false)
    private Integer percentageBps;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "vat_bps", nullable = false)
    private Integer vatBps;

    @NotNull
    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Size(max = 240)
    @Column(name = "note", length = 240)
    private String note;

    @Size(max = 64)
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Rate card baru. Jangan pernah mengubah baris lama — buat baris baru saat rate berubah
     * (lihat {@code effectiveFrom} / {@code effectiveTo}).
     *
     * @param providerId null untuk fee platform (global); diisi untuk GATEWAY_PROCESSING/PAYOUT
     * @param channelId  null untuk fee platform (global); diisi untuk GATEWAY_PROCESSING/PAYOUT
     */
    public static FeeConfig create(
            FeeType feeType,
            Long providerId,
            Long channelId,
            Long fixedAmount,
            Integer percentageBps,
            Integer vatBps,
            Instant effectiveFrom,
            String note,
            String createdBy
    ){
        FeeConfig f = new FeeConfig();
        f.feeType = feeType;
        f.providerId = providerId;
        f.channelId = channelId;
        f.fixedAmount = fixedAmount;
        f.percentageBps = percentageBps;
        f.vatBps = vatBps;
        f.effectiveFrom = effectiveFrom;
        f.note = note;
        f.createdBy = createdBy;
        f.createdAt = Instant.now();
        return f;
    }

}