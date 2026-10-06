package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.EvidenceSource;
import com.gepe.gepay.payment.api.enums.SettlementStatus;
import com.gepe.gepay.payment.api.enums.SettlementTarget;
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
@Table(name = "settlements", schema = "payment")
public class Settlement {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private SettlementStatus status;

    @Size(max = 120)
    @Column(name = "external_settlement_id", length = 120)
    private String externalSettlementId;

    @Column(name = "expected_amount")
    private Long expectedAmount;

    @Column(name = "actual_amount")
    private Long actualAmount;

    @Column(name = "variance_amount")
    private Long varianceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_target", length = 20)
    private SettlementTarget settlementTarget;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "actual_settled_at")
    private Instant actualSettledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_source", length = 30)
    private EvidenceSource evidenceSource;

    @Size(max = 200)
    @Column(name = "evidence_reference", length = 200)
    private String evidenceReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_evidence")
    private Map<String, Object> rawEvidence;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Header batch pencairan dana dari PG (dibuat dari BUKTI: CSV/report +
     * mutasi bank). Status awal PENDING; baru CONFIRMED setelah bukti nominal
     * dana masuk diverifikasi.
     */
    public static Settlement create(
            Long providerId,
            SettlementTarget settlementTarget,
            String externalSettlementId,
            EvidenceSource evidenceSource,
            String evidenceReference,
            LocalDate periodStart,
            LocalDate periodEnd,
            UUID createdBy
    ){
        Settlement s = new Settlement();
        s.id = UuidCreator.getTimeOrderedEpoch();
        s.providerId = providerId;
        s.status = SettlementStatus.PENDING;
        s.settlementTarget = settlementTarget;
        s.externalSettlementId = externalSettlementId;
        s.evidenceSource = evidenceSource;
        s.evidenceReference = evidenceReference;
        s.periodStart = periodStart;
        s.periodEnd = periodEnd;
        s.createdBy = createdBy;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    public void matchExpected(long expectedAmount) {
        this.expectedAmount = expectedAmount;
        this.updatedAt = Instant.now();
    }

    public void confirm(long actualAmount, Instant actualSettledAt) {
        this.status = SettlementStatus.CONFIRMED;
        this.actualAmount = actualAmount;
        this.actualSettledAt = actualSettledAt;
        this.varianceAmount = actualAmount - (expectedAmount == null ? 0L : expectedAmount);
        this.confirmedAt = Instant.now();
        this.updatedAt = this.confirmedAt;
    }

    public void cancel() {
        this.status = SettlementStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

}
