package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.ReconciliationRunStatus;
import com.gepe.gepay.payment.api.enums.ReconciliationRunType;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "reconciliation_runs", schema = "payment")
public class ReconciliationRun {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "type", nullable = false, length = 20)
    private ReconciliationRunType type;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationRunStatus status;

    @Column(name = "period_start")
    private Instant periodStart;

    @Column(name = "period_end")
    private Instant periodEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary")
    private Map<String, Object> summary;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;


    public static ReconciliationRun create(
            ReconciliationRunType type,
            Instant periodStart,
            Instant periodEnd
    ){
        ReconciliationRun r = new ReconciliationRun();
        r.id = UuidCreator.getTimeOrderedEpoch();
        r.type = type;
        r.periodStart = periodStart;
        r.periodEnd = periodEnd;
        r.status = ReconciliationRunStatus.RUNNING;
        r.startedAt = Instant.now();
        return r;
    }

}