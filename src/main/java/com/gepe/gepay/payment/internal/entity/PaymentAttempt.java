package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.PaymentAttemptStatus;
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
@Table(name = "payment_attempts", schema = "payment")
public class PaymentAttempt {
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
    @Column(name = "channel_route_id", nullable = false)
    private Long channelRouteId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_route_id", insertable = false, updatable = false)
    private ChannelRoute channelRoute;

    @Size(max = 120)
    @Column(name = "provider_reference_id", length = 120)
    private String providerReferenceId;

    @Size(max = 120)
    @Column(name = "payment_reference_number", length = 120)
    private String paymentReferenceNumber;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "status", nullable = false, length = 20)
    private PaymentAttemptStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_request")
    private Map<String, Object> rawRequest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response")
    private Map<String, Object> rawResponse;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentAttempt create(
            UUID paymentId,
            Long channelRouteId,
            String providerReferenceId,
            String paymentReferenceNumber,
            Instant expiresAt
    ){
        PaymentAttempt a = new PaymentAttempt();
        a.id = UuidCreator.getTimeOrderedEpoch();
        a.paymentId = paymentId;
        a.channelRouteId = channelRouteId;
        a.providerReferenceId = providerReferenceId;
        a.paymentReferenceNumber = paymentReferenceNumber;
        a.status = PaymentAttemptStatus.INITIATED;
        a.expiresAt = expiresAt;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

}