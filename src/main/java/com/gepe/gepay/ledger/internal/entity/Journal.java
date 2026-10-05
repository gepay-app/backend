package com.gepe.gepay.ledger.internal.entity;

import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "journals", schema = "ledger")
public class Journal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Size(max = 160)
    @NotNull
    @Column(name = "idempotency_key", nullable = false, length = 160, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(name = "reference_type", nullable = false, length = 40, updatable = false)
    private JournalReferenceType referenceType;

    @Size(max = 64)
    @NotNull
    @Column(name = "reference_id", nullable = false, length = 64, updatable = false)
    private String referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_journal_id", updatable = false)
    private Journal reversesJournal;

    @Size(max = 255)
    @NotNull
    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @NotNull
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;



    @CreationTimestamp
    @Column(name = "created_at", nullable = false,updatable = false)
    private Instant createdAt;


    public static Journal create(
            String idempotencyKey,
            JournalReferenceType referenceType, String referenceId,
            String description, Instant occurredAt) {
        Journal j = new Journal();
        j.idempotencyKey = idempotencyKey;
        j.referenceType = referenceType;
        j.referenceId = referenceId;
        j.description = description;
        j.occurredAt = occurredAt;

        return j;
    }

    public static Journal createReversal(
            String idempotencyKey,
            JournalReferenceType referenceType, String referenceId,
            String description, Instant occurredAt, Journal reversesJournal) {
        Journal j = create(idempotencyKey,referenceType, referenceId, description, occurredAt);
        j.reversesJournal = reversesJournal;

        return j;
    }
}