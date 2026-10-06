package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.FundTransferDirection;
import com.gepe.gepay.payment.api.enums.FundTransferSourceType;
import com.gepe.gepay.payment.api.enums.FundTransferStatus;
import com.gepe.gepay.payment.api.enums.FundTransferTargetType;
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
@Table(name = "fund_transfers", schema = "payment")
public class FundTransfer {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "direction", nullable = false, length = 20)
    private FundTransferDirection direction;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "source_type", nullable = false, length = 20)
    private FundTransferSourceType sourceType;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "target_type", nullable = false, length = 20)
    private FundTransferTargetType targetType;

    @NotNull
    @Column(name = "sent_amount", nullable = false)
    private Long sentAmount;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Column(name = "received_amount")
    private Long receivedAmount;

    @Column(name = "variance_amount")
    private Long varianceAmount;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private FundTransferStatus status;

    @Size(max = 120)
    @Column(name = "bank_reference", length = 120)
    private String bankReference;

    @Size(max = 120)
    @Column(name = "provider_reference", length = 120)
    private String providerReference;

    @Size(max = 240)
    @Column(name = "note", length = 240)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;


    public static FundTransfer create(
            FundTransferDirection direction,
            FundTransferSourceType sourceType,
            FundTransferTargetType targetType,
            Long sentAmount,
            Long feeAmount,
            String note,
            UUID createdBy
    ){
        FundTransfer f = new FundTransfer();
        f.id = UuidCreator.getTimeOrderedEpoch();
        f.direction = direction;
        f.sourceType = sourceType;
        f.targetType = targetType;
        f.sentAmount = sentAmount;
        f.feeAmount = feeAmount;
        f.note = note;
        f.createdBy = createdBy;
        f.status = FundTransferStatus.PENDING;
        f.createdAt = Instant.now();
        return f;
    }

}