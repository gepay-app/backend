package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.RefundStatus;
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
import java.util.Map;
import java.util.UUID;

@NoArgsConstructor
@Getter

@Entity
@Table(name = "refunds", schema = "payment")
public class Refund {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    private Payment payment;

    @NotNull
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private RefundStatus status;

    @NotNull
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Size(max = 240)
    @Column(name = "reason", length = 240)
    private String reason;

    @Size(max = 120)
    @Column(name = "provider_reference_id", length = 120)
    private String providerReferenceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload")
    private Map<String, Object> rawPayload;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static Refund create(UUID paymentId, Long providerId, Long amount, String reason){
        Refund r = new Refund();
        r.id = UuidCreator.getTimeOrderedEpoch();
        r.paymentId = paymentId;
        r.providerId = providerId;
        r.status = RefundStatus.REQUESTED;
        r.amount = amount;
        r.reason = reason;
        r.createdAt = Instant.now();
        return r;
    }

}