package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.AdjustmentScope;
import com.gepe.gepay.payment.api.enums.AdjustmentStatus;
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
@Table(name = "adjustments", schema = "payment")
public class Adjustment {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "scope", nullable = false, length = 20)
    private AdjustmentScope scope;

    @Size(max = 40)
    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Size(max = 64)
    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "journal_id")
    private Long journalId;

    @Size(max = 300)
    @NotNull
    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Size(max = 200)
    @Column(name = "evidence_reference", length = 200)
    private String evidenceReference;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private AdjustmentStatus status;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "posted_at")
    private Instant postedAt;


    /**
     * Pengajuan koreksi (maker-checker). Status awal PENDING; pemanggil yang
     * menyetujui harus beda orang dari {@code requestedBy}.
     */
    public static Adjustment create(
            AdjustmentScope scope,
            String referenceType,
            String referenceId,
            Long amount,
            String reason,
            String evidenceReference,
            UUID requestedBy
    ){
        Adjustment a = new Adjustment();
        a.id = UuidCreator.getTimeOrderedEpoch();
        a.scope = scope;
        a.referenceType = referenceType;
        a.referenceId = referenceId;
        a.amount = amount;
        a.reason = reason;
        a.evidenceReference = evidenceReference;
        a.requestedBy = requestedBy;
        a.status = AdjustmentStatus.PENDING;
        a.createdAt = Instant.now();
        return a;
    }

}