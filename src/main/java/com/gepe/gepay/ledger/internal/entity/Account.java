package com.gepe.gepay.ledger.internal.entity;

import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "accounts", schema = "ledger")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = AccountCode.JpaConverter.class)
    @Column(name = "code", nullable = false, length = 20, updatable = false)
    private AccountCode code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10, updatable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 6, updatable = false)
    private EntryDirection normalBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", length = 24, updatable = false)
    private AccountOwnerType ownerType;

    @Column(name = "owner_ref", length = 64, updatable = false)
    private String ownerRef;

    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency = "IDR";

    @Column(name = "balance", nullable = false)
    private long balance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Dipanggil dari service setelah entries dibuat.
     */
    public void apply(EntryDirection direction, long amount) {
        boolean increase = direction.name().equals(normalBalance.name());
        this.balance += increase ? amount : -amount;
    }

    public static Account create(AccountCode code, String ownerRef) {
        Account a = new Account();
        a.code = code;
        a.name = code.getDisplayName();
        a.type = code.getType();
        a.normalBalance = code.getType().getNormalBalance();
        a.ownerType = code.getOwnerType();
        a.ownerRef = ownerRef;
        return a;
    }
}