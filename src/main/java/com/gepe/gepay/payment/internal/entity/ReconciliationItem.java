package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.ReconciliationItemStatus;
import com.gepe.gepay.payment.api.enums.ReconciliationResolutionAction;
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
@Table(name = "reconciliation_items", schema = "payment")
public class ReconciliationItem {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", insertable = false, updatable = false)
    private ReconciliationRun run;

    @Size(max = 200)
    @NotNull
    @Column(name = "match_key", nullable = false, length = 200)
    private String matchKey;

    @Size(max = 120)
    @Column(name = "internal_ref", length = 120)
    private String internalRef;

    @Size(max = 120)
    @Column(name = "external_ref", length = 120)
    private String externalRef;

    @Column(name = "internal_amount")
    private Long internalAmount;

    @Column(name = "external_amount")
    private Long externalAmount;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 30)
    private ReconciliationItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", length = 60)
    private ReconciliationResolutionAction resolutionAction;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Size(max = 300)
    @Column(name = "note", length = 300)
    private String note;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ReconciliationItem create(
            UUID runId,
            String matchKey,
            String internalRef,
            String externalRef,
            Long internalAmount,
            Long externalAmount,
            ReconciliationItemStatus status
    ){
        ReconciliationItem i = new ReconciliationItem();
        i.id = UuidCreator.getTimeOrderedEpoch();
        i.runId = runId;
        i.matchKey = matchKey;
        i.internalRef = internalRef;
        i.externalRef = externalRef;
        i.internalAmount = internalAmount;
        i.externalAmount = externalAmount;
        i.status = status;
        i.createdAt = Instant.now();
        return i;
    }

}