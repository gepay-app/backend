package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.FeeType;
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
@Table(name = "user_fee_overrides", schema = "payment")
public class UserFeeOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "fee_type", nullable = false, length = 30)
    private FeeType feeType;

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

    @Size(max = 300)
    @NotNull
    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @NotNull
    @Column(name = "approved_by", nullable = false)
    private UUID approvedBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;


    /**
     * Override fee per user (mis. VIP). Baris baru tiap perubahan — jangan update
     * baris lama; isi {@code effectiveTo} saat digantikan.
     */
    public static UserFeeOverride create(
            UUID userId,
            FeeType feeType,
            Long fixedAmount,
            Integer percentageBps,
            Integer vatBps,
            Instant effectiveFrom,
            String reason,
            UUID approvedBy
    ){
        UserFeeOverride o = new UserFeeOverride();
        o.userId = userId;
        o.feeType = feeType;
        o.fixedAmount = fixedAmount;
        o.percentageBps = percentageBps;
        o.vatBps = vatBps;
        o.effectiveFrom = effectiveFrom;
        o.reason = reason;
        o.approvedBy = approvedBy;
        o.createdAt = Instant.now();
        return o;
    }

}