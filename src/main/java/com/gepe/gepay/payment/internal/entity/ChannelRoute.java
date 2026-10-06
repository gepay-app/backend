package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.SettlementTarget;
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
@Table(name = "channel_routes", schema = "payment")
public class ChannelRoute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

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

    @Size(max = 60)
    @NotNull
    @Column(name = "provider_channel_code", nullable = false, length = 60)
    private String providerChannelCode;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "min_amount", nullable = false)
    private Long minAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "max_amount", nullable = false)
    private Long maxAmount;

    @NotNull
    @ColumnDefault("100")
    @Column(name = "priority", nullable = false)
    private Integer priority;

    @NotNull
    @ColumnDefault("3")
    @Column(name = "settlement_delay_days", nullable = false)
    private Integer settlementDelayDays;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "settlement_target", nullable = false, length = 20)
    private SettlementTarget settlementTarget;

    @NotNull
    @ColumnDefault("true")
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ChannelRoute create(
            Long providerId,
            Long channelId,
            String providerChannelCode,
            Long minAmount,
            Long maxAmount,
            Integer priority,
            Integer settlementDelayDays,
            SettlementTarget settlementTarget
    ){
        ChannelRoute r = new ChannelRoute();
        r.providerId = providerId;
        r.channelId = channelId;
        r.providerChannelCode = providerChannelCode;
        r.minAmount = minAmount;
        r.maxAmount = maxAmount;
        r.priority = priority;
        r.settlementDelayDays = settlementDelayDays;
        r.settlementTarget = settlementTarget;
        r.isActive = true;
        r.createdAt = Instant.now();
        r.updatedAt = r.createdAt;
        return r;
    }

}