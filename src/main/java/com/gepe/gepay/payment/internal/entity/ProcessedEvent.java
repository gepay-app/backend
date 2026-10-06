package com.gepe.gepay.payment.internal.entity;

import com.gepe.gepay.payment.api.enums.ProcessedEventType;
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
@Table(name = "processed_events", schema = "payment")
public class ProcessedEvent {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "provider_id")
    private Long providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", insertable = false, updatable = false)
    private Provider provider;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "event_type", nullable = false, length = 60)
    private ProcessedEventType eventType;

    @Size(max = 160)
    @NotNull
    @Column(name = "external_event_id", nullable = false, length = 160)
    private String externalEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private Map<String, Object> payload;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /**
     * Inbox webhook. Uniqueness di DB (provider, externalEventId) yang mencegah
     * event dobel — bukan pengecekan di layer aplikasi.
     */
    public static ProcessedEvent create(
            Long providerId,
            ProcessedEventType eventType,
            String externalEventId,
            Map<String, Object> payload
    ){
        ProcessedEvent e = new ProcessedEvent();
        e.id = UuidCreator.getTimeOrderedEpoch();
        e.providerId = providerId;
        e.eventType = eventType;
        e.externalEventId = externalEventId;
        e.payload = payload;
        e.processedAt = Instant.now();
        return e;
    }

}