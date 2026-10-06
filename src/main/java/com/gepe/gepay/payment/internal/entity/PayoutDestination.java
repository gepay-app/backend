package com.gepe.gepay.payment.internal.entity;

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
@Table(name = "payout_destinations", schema = "payment")
public class PayoutDestination {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull
    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", insertable = false, updatable = false)
    private Channel channel;

    @Size(max = 40)
    @NotNull
    @Column(name = "account_number", nullable = false, length = 40)
    private String accountNumber;

    @Size(max = 120)
    @NotNull
    @Column(name = "account_name", nullable = false, length = 120)
    private String accountName;

    @Size(max = 20)
    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

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

    public static PayoutDestination create(
            UUID userId,
            Long channelId,
            String accountNumber,
            String accountName,
            String bankCode
    ){
        PayoutDestination d = new PayoutDestination();
        d.id = UuidCreator.getTimeOrderedEpoch();
        d.userId = userId;
        d.channelId = channelId;
        d.accountNumber = accountNumber;
        d.accountName = accountName;
        d.bankCode = bankCode;
        d.isDefault = false;
        d.isActive = true;
        d.createdAt = Instant.now();
        d.updatedAt = d.createdAt;
        return d;
    }

}