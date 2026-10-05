package com.gepe.gepay.ledger.internal.entity;

import com.gepe.gepay.ledger.api.enums.EntryDirection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "entries", schema = "ledger")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private Long journalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_id", insertable = false, updatable = false)
    private Journal journal;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6, updatable = false)
    private EntryDirection direction;

    @Column(nullable = false, updatable = false)
    private long amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Entry of(Long journalId, Long accountId, EntryDirection direction, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
        Entry e = new Entry();
        e.journalId = journalId;
        e.accountId = accountId;
        e.direction = direction;
        e.amount = amount;
        return e;
    }
}