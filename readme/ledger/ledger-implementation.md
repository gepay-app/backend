# Ledger Module — Detailed Implementation Plan

> Generated from analysis of `design.md`, `example.md`, existing entities, and migrations.  
> Follows `../../AGENTS.md` conventions: `api`/`internal` split, `NamedInterface("api")`, module i18n, `ErrorCode` enum, `ServiceException`, `ModularityTests`.
>
> **Terminology**: Uses exact names from `AccountCode` enum & `design.md`:
> - `PG_CLEARING_RECEIVABLE` (1100) — Piutang ke PG payin (uang ditangkap PG, belum settle)
> - `PAYIN_PROVIDER_BALANCE` (1150) — Saldo platform di akun PG payin (sudah settle, belum ke bank)
> - `BANK_OPERATING` (1200) — Rekening bank operasional platform
> - `PAYOUT_PROVIDER_FLOAT` (1300) — Float di payout provider (Flip) siap disburse
> - `FUND_TRANSFER_IN_TRANSIT` (1400) — Dana sedang transfer antar provider (lewat bank)
> - `CREATOR_PAYABLE_PENDING` (2100) — Hak creator belum bisa tarik (menunggu settle)
> - `CREATOR_PAYABLE_AVAILABLE` (2110) — Hak creator siap ditarik (sudah settle)
> - `WITHDRAWAL_PAYABLE` (2200) — Dana creator di-hold saat withdrawal diproses
> - `VAT_PAYABLE` (2300) — PPN utang negara
> - `PLATFORM_FEE_REVENUE` (4000) — Revenue fee platform
> - `WITHDRAWAL_FEE_REVENUE` (4100) — Revenue fee withdrawal
> - `PAYMENT_GATEWAY_FEE_EXPENSE` (5000) — Expense fee PG (kalau platform bayar)
> - `PAYOUT_FEE_EXPENSE` (5100) — Expense fee payout provider
> - `BANK_TRANSFER_FEE_EXPENSE` (5150) — Expense biaya transfer bank
> - `REFUND_CHARGEBACK_LOSS` (5200) — Kerugian refund/chargeback
> - `CREATOR_NEGATIVE_BALANCE` (5300) — Piutang ke creator (clawback)
> - `FUND_TRANSFER_VARIANCE` (5900) — Selisih transfer tak terjelaskan

---

## 1. File Structure (Target)

```
src/main/java/com/gepe/gepay/ledger/
├── package-info.java                          ✅ exists (CLOSED, id="ledger")
├── api/
│   ├── package-info.java                      ✅ exists (@NamedInterface("api"))
│   ├── enums/                                 🆕 shared enums (used by DTOs & other modules)
│   │   ├── AccountCode.java                   🆕 (moved from internal)
│   │   ├── AccountType.java                   🆕
│   │   ├── EntryDirection.java                🆕
│   │   ├── AccountOwnerType.java              🆕
│   │   └── JournalReferenceType.java          🆕
│   ├── dto/
│   │   ├── JournalLine.java                   🆕 record
│   │   ├── AccountBalance.java                🆕 record
│   │   ├── PostJournalRequest.java            🆕 record (internal use by payment)
│   │   └── PostJournalResult.java             🆕 record
│   └── event/
│       └── LedgerEvent.java                   🆕 (future: if other modules need async)
└── internal/
    ├── config/
    │   └── LedgerCacheConfig.java             🆕 CacheSpec beans
    ├── delivery/http/                         🆕 (optional: admin/debug endpoints only)
    │   ├── req/
    │   ├── res/
    │   └── LedgerController.java
    ├── entity/
    │   ├── Account.java                       ✅ exists (imports api.enums)
    │   ├── Entry.java                         ✅ exists (imports api.enums)
    │   └── Journal.java                       ✅ exists (imports api.enums)
    ├── exception/
    │   └── LedgerError.java                   🆕 implements ErrorCode
    ├── listener/                              🆕 (future: if consuming events)
    ├── mapper/
    │   └── LedgerMapper.java                  🆕 internal ↔ api DTO
    ├── repository/
    │   ├── AccountRepository.java             🆕
    │   ├── EntryRepository.java               🆕
    │   └── JournalRepository.java             🆕
    ├── service/
    │   ├── AccountService.java                🆕
    │   ├── LedgerService.java                 🆕 (core posting logic)
    │   └── BalanceQueryService.java           🆕 (read path, cached)
    └── validator/
        └── JournalValidator.java              🆕 (debit=credit, idempotency)
```

---

## 2. API Package (`ledger/api/`)

### 2.1 `LedgerApi.java` — Facade Interface

```java
package com.gepe.gepay.ledger.api;

import com.gepe.gepay.ledger.api.dto.*;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import java.time.Instant;
import java.util.List;

public interface LedgerApi {

    // ---------------------------------------------------------------------
    // Account — lazy get-or-create (called by payment module)
    // ---------------------------------------------------------------------
    AccountBalance getOrCreateAccount(AccountCode code, String ownerRef);

    AccountBalance getAccount(AccountCode code, String ownerRef);

    // ---------------------------------------------------------------------
    // Journal — core write path
    // ---------------------------------------------------------------------
    /**
     * Posts a balanced journal. Idempotent via idempotencyKey.
     * Throws LedgerError.UNBALANCED_JOURNAL if Σdebit ≠ Σcredit.
     * Throws LedgerError.IDEMPOTENCY_CONFLICT if key already exists.
     *
     * @param idempotencyKey   format: "{referenceType}:{referenceId}:{action}" e.g. "PAYMENT:PAY-1:PAID"
     * @param referenceType    PAYMENT | SETTLEMENT | WITHDRAWAL | PAYOUT | FUND_TRANSFER | REFUND | ADJUSTMENT
     * @param referenceId      business ID (paymentId, settlementId, withdrawalId, payoutId, fundTransferId)
     * @param description      human-readable description for audit
     * @param occurredAt       business timestamp (when event happened, not system time)
     * @param lines            min 2 lines, ΣDEBIT = ΣCREDIT, amount > 0
     * @param reversesJournalId  nullable, for reversal/correction journals
     */
    PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId  // nullable, for reversal journals
    );

    // Convenience overload for payment module (builds idempotencyKey internally)
    PostJournalResult postJournal(
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    );

    // ---------------------------------------------------------------------
    // Balance queries (read path, cached)
    // ---------------------------------------------------------------------
    AccountBalance getBalance(AccountCode code, String ownerRef);

    long getBalanceAmount(AccountCode code, String ownerRef);
}
```

### 2.2 DTO Records (`ledger/api/dto/`)

```java
// JournalLine.java
package com.gepe.gepay.ledger.api.dto;

import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.EntryDirection;

public record JournalLine(
        AccountCode accountCode,        // e.g. PG_CLEARING_RECEIVABLE
        String ownerRef,                // providerId, userId, "BANK-1", or null for global
        EntryDirection direction,       // DEBIT or CREDIT
        long amount                     // positive, in IDR (rupiah bulat)
) {
    // No validation here — done in JournalValidator with i18n via ServiceException
}

// AccountBalance.java
package com.gepe.gepay.ledger.api.dto;

import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import java.util.Objects;

public record AccountBalance(
        long id,
        AccountCode code,
        String name,
        AccountType type,
        EntryDirection normalBalance,
        String ownerType,      // USER, PAYMENT_PROVIDER, PAYOUT_PROVIDER, BANK, or null
        String ownerRef,
        String currency,
        long balance,          // in normal balance direction
        boolean active
) {
    public AccountBalance {
        Objects.requireNonNull(code);
        Objects.requireNonNull(name);
        Objects.requireNonNull(type);
        Objects.requireNonNull(normalBalance);
        Objects.requireNonNull(currency);
    }
}

// PostJournalRequest.java (internal use by payment module builder)
package com.gepe.gepay.ledger.api.dto;

import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import java.time.Instant;
import java.util.List;

public record PostJournalRequest(
        String idempotencyKey,
        JournalReferenceType referenceType,
        String referenceId,
        String description,
        Instant occurredAt,
        List<JournalLine> lines,
        Long reversesJournalId
) {}

// PostJournalResult.java
package com.gepe.gepay.ledger.api.dto;

public record PostJournalResult(
        long journalId,
        String idempotencyKey,
        boolean newlyCreated   // false if idempotent replay (already existed)
) {}
```

---

## 3. Internal Package (`ledger/internal/`)

### 3.1 Repository Interfaces

```java
// AccountRepository.java
package com.gepe.gepay.ledger.internal.repository;

import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Exact match by code + owner_type + owner_ref
    Optional<Account> findByCodeAndOwnerTypeAndOwnerRef(
            AccountCode code, AccountOwnerType ownerType, String ownerRef);

    // For lazy get-or-create (NON-locking read — used by postJournal after sorting)
    Optional<Account> findByCodeAndOwnerTypeAndOwnerRefNoLock(
            AccountCode code, AccountOwnerType ownerType, String ownerRef);

    // Batched pessimistic lock in deterministic order (deadlock prevention)
    // Uses native query with array_position for exact lock order matching Java sort
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM ledger.accounts
            WHERE id = ANY(:ids)
            ORDER BY array_position(:ids::bigint[], id)
            FOR UPDATE
            """, nativeQuery = true)
    List<Account> lockAllByIds(@Param("ids") List<Long> ids);

    // Find all sub-accounts for a given owner (e.g. all accounts for a creator)
    List<Account> findByOwnerTypeAndOwnerRef(AccountOwnerType ownerType, String ownerRef);

    // Global accounts (owner_type IS NULL)
    List<Account> findByOwnerTypeIsNull();
}
```

```java
// JournalRepository.java
package com.gepe.gepay.ledger.internal.repository;

import com.gepe.gepay.ledger.internal.entity.Journal;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalRepository extends JpaRepository<Journal, Long> {

    Optional<Journal> findByIdempotencyKey(String idempotencyKey);

    List<Journal> findByReferenceTypeAndReferenceId(
            JournalReferenceType referenceType, String referenceId);

    @Query("SELECT j FROM Journal j WHERE j.referenceType = :refType AND j.referenceId = :refId ORDER BY j.id DESC")
    List<Journal> findByReferenceTypeAndReferenceIdOrderByIdDesc(
            @Param("refType") JournalReferenceType referenceType,
            @Param("refId") String referenceId);
}
```

```java
// EntryRepository.java
package com.gepe.gepay.ledger.internal.repository;

import com.gepe.gepay.ledger.internal.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByJournalId(Long journalId);

    List<Entry> findByAccountIdOrderById(Long accountId);

    @Query("SELECT SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amount ELSE -e.amount END) "
           + "FROM Entry e WHERE e.accountId = :accountId")
    Long computeBalance(@Param("accountId") Long accountId);
}
```

### 3.2 Service Layer

#### `AccountService.java` — Lazy account creation (matches `AccountCode` enum)

```java
package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountBalance;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.ledger.internal.mapper.LedgerMapper;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerMapper mapper;

    @Transactional
    public AccountBalance getOrCreateAccount(AccountCode code, String ownerRef) {
        AccountOwnerType ownerType = code.getOwnerType();
        String codeStr = code.getCode();

        // 1. Plain non-locking read — fast path for existing accounts
        var existing = accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code, ownerType, ownerRef);
        if (existing.isPresent()) {
            return mapper.toBalance(existing.get());
        }

        // 2. Not found — INSERT and let DB unique constraint (ux_accounts_code_owner) handle race.
        //    No SELECT FOR UPDATE on non-existent row (prevents gap locking issues).
        Account account = Account.create(code, ownerRef);
        try {
            Account saved = accountRepository.saveAndFlush(account);
            log.debug("Created ledger account: code={}, name={}, ownerType={}, ownerRef={}, id={}",
                    codeStr, code.getDisplayName(), ownerType, ownerRef, saved.getId());
            return mapper.toBalance(saved);
        } catch (DataIntegrityViolationException e) {
            // Concurrent INSERT won — re-read
            Account winner = accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code, ownerType, ownerRef)
                    .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND, codeStr, ownerRef));
            return mapper.toBalance(winner);
        }
    }

    @Transactional(readOnly = true)
    public AccountBalance getAccount(AccountCode code, String ownerRef) {
        return accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code, code.getOwnerType(), ownerRef)
                .map(mapper::toBalance)
                .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND, code.getCode(), ownerRef));
    }
}
```

---

### Deadlock Prevention Strategy (Critical)

The `postJournal` implementation follows these principles to **guarantee deadlock-free** operation under high concurrency:

| Principle | Implementation |
|-----------|----------------|
| **Deterministic lock order** | All account IDs sorted ascending globally (`sortedAccountIds`) before locking |
| **Single batched lock** | One `SELECT ... FOR UPDATE` for all accounts in sorted order (`accountRepository.lockAllByIds(sortedAccountIds)`) |
| **No double-locking** | `getOrCreateAccount` does plain read/INSERT; lock acquired only once in batch |
| **DB constraint as source of truth** | Idempotency via unique index on `journals.idempotency_key` (no SELECT pre-check race) |
| **Optimistic lock backstop** | `@Version` on Account catches any bypassed-lock updates |

**Why this works:**
- Circular wait is impossible because every transaction acquires locks in the **exact same global ID order** (e.g. ID 1 then ID 5 then ID 10)
- PostgreSQL's native `ORDER BY array_position(:ids::bigint[], id)` in `FOR UPDATE` query ensures locks are taken in exact sorted order
- Max 2 database round-trips: (1) resolve/create accounts, (2) batched lock — no retry loops
- Idempotency race handled by DB unique constraint (`CONSTRAINT ux_journals_idem UNIQUE`)

#### `JournalValidator.java` — Validation logic

```java
package com.gepe.gepay.ledger.internal.validator;

import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public final class JournalValidator {

    public static void validate(List<JournalLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new ServiceException(LedgerError.JOURNAL_MIN_LINES);
        }

        long debitSum = 0L;
        long creditSum = 0L;

        for (JournalLine line : lines) {
            if (line.amount() <= 0) {
                throw new ServiceException(LedgerError.INVALID_AMOUNT, line.accountCode().getCode());
            }
            if (line.direction() == EntryDirection.DEBIT) {
                debitSum += line.amount();
            } else {
                creditSum += line.amount();
            }
        }

        if (debitSum != creditSum) {
            throw new ServiceException(LedgerError.UNBALANCED_JOURNAL, debitSum, creditSum);
        }
    }
}
```

#### `LedgerService.java` — Core posting logic (double-entry, idempotent, append-only)

```java
package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dtos.AccountBalance;
import com.gepe.gepay.ledger.api.dtos.JournalLine;
import com.gepe.gepay.ledger.api.dtos.PostJournalResult;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.JournalReferenceType;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.internal.entity.Entry;
import com.gepe.gepay.ledger.internal.entity.Journal;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.ledger.internal.mapper.LedgerMapper;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import com.gepe.gepay.ledger.internal.repository.EntryRepository;
import com.gepe.gepay.ledger.internal.repository.JournalRepository;
import com.gepe.gepay.ledger.internal.validator.JournalValidator;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final JournalRepository journalRepository;
    private final EntryRepository entryRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    @Transactional
    public PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    ) {
        // 1. Check idempotency pre-check
        var existingJournal = journalRepository.findByIdempotencyKey(idempotencyKey);
        if (existingJournal.isPresent()) {
            Journal existing = existingJournal.get();
            log.debug("Idempotent replay (pre-check): key={}, journalId={}", idempotencyKey, existing.getId());
            return new PostJournalResult(existing.getId(), idempotencyKey, false);
        }

        // 2. Validate double-entry rules: ΣDEBIT = ΣCREDIT, min 2 lines, amount > 0
        JournalValidator.validate(lines);

        // 3. Resolve / get-or-create distinct accounts WITHOUT locking
        Map<AccountKey, Account> resolvedAccounts = new HashMap<>();
        for (JournalLine line : lines) {
            AccountKey key = new AccountKey(line.accountCode(), line.ownerRef());
            if (!resolvedAccounts.containsKey(key)) {
                AccountBalance balance = accountService.getOrCreateAccount(line.accountCode(), line.ownerRef());
                Account acc = accountRepository.findById(balance.id())
                        .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND,
                                line.accountCode().getCode(), line.ownerRef()));
                resolvedAccounts.put(key, acc);
            }
        }

        // 4. Deterministic sorting & batched pessimistic locking (DEADLOCK PREVENTION)
        // Sort IDs ascending so all concurrent threads acquire locks in the EXACT SAME GLOBAL ORDER
        List<Long> sortedAccountIds = resolvedAccounts.values().stream()
                .map(Account::getId)
                .distinct()
                .sorted()
                .toList();

        Map<Long, Account> lockedAccountMap = new HashMap<>();
        if (!sortedAccountIds.isEmpty()) {
            List<Account> lockedList = accountRepository.lockAllByIds(sortedAccountIds);
            lockedAccountMap = lockedList.stream()
                    .collect(Collectors.toMap(Account::getId, Function.identity()));
        }

        // 5. Create Journal Entity — catch DB unique constraint on idempotency_key for race handling
        Journal journal = Journal.create(idempotencyKey, referenceType, referenceId, description, occurredAt);
        if (reversesJournalId != null) {
            Journal reverses = journalRepository.findById(reversesJournalId)
                    .orElseThrow(() -> new ServiceException(LedgerError.REVERSES_JOURNAL_NOT_FOUND, reversesJournalId));
            journal = Journal.createReversal(idempotencyKey, referenceType, referenceId, description, occurredAt, reverses);
        }

        Journal savedJournal;
        try {
            savedJournal = journalRepository.saveAndFlush(journal);
        } catch (DataIntegrityViolationException e) {
            // Idempotency key race condition — another request posted with the same key
            Journal existing = journalRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new ServiceException(LedgerError.IDEMPOTENCY_CONFLICT, idempotencyKey));
            log.debug("Idempotent replay (DB race): key={}, journalId={}", idempotencyKey, existing.getId());
            return new PostJournalResult(existing.getId(), idempotencyKey, false);
        }

        // 6. Create Entries + update Account balances
        List<Entry> entries = new ArrayList<>(lines.size());
        for (JournalLine line : lines) {
            AccountKey key = new AccountKey(line.accountCode(), line.ownerRef());
            Account unlockedAcc = resolvedAccounts.get(key);
            Account lockedAcc = lockedAccountMap.get(unlockedAcc.getId());

            if (!lockedAcc.isActive()) {
                throw new ServiceException(LedgerError.ACCOUNT_INACTIVE, line.accountCode().getCode());
            }

            Entry entry = Entry.of(savedJournal.getId(), lockedAcc.getId(), line.direction(), line.amount());
            entries.add(entry);

            // Mutate balance
            lockedAcc.apply(line.direction(), line.amount());
        }

        entryRepository.saveAll(entries);
        accountRepository.saveAll(lockedAccountMap.values()); // Triggers optimistic version increment

        log.info("Posted journal successfully: id={}, refType={}, refId={}, lines={}, idemKey={}",
                savedJournal.getId(), referenceType, referenceId, lines.size(), idempotencyKey);

        return new PostJournalResult(savedJournal.getId(), idempotencyKey, true);
    }

    @Transactional
    public PostJournalResult postJournal(
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    ) {
        String idemKey = referenceType.name() + ":" + referenceId + ":" + Integer.toHexString(description.hashCode());
        return postJournal(idemKey, referenceType, referenceId, description, occurredAt, lines, reversesJournalId);
    }

    private record AccountKey(AccountCode code, String ownerRef) {}
}
```


**Idempotency key format (payment module should use explicit keys):**

| JournalReferenceType | Idempotency Key Format | Example |
|---------------------|------------------------|---------|
| `PAYMENT` | `PAYMENT:{paymentId}:PAID` | `PAYMENT:PAY-1:PAID` |
| `SETTLEMENT` | `SETTLEMENT:{settlementId}:CONFIRMED` / `SETTLEMENT:{settlementId}:RELEASE` | `SETTLEMENT:SET-1:CONFIRMED` |
| `WITHDRAWAL` | `WITHDRAWAL:{withdrawalId}:HOLD` | `WITHDRAWAL:WD-1:HOLD` |
| `PAYOUT` | `PAYOUT:{payoutId}:COMPLETED` / `PAYOUT:{payoutId}:FAILED` | `PAYOUT:PO-1:COMPLETED` |
| `FUND_TRANSFER` | `FUND_TRANSFER:{fundTransferId}` | `FUND_TRANSFER:FT-1` |
| `REFUND` | `REFUND:{refundId}` | `REFUND:RF-1` |
| `ADJUSTMENT` | `ADJUSTMENT:{adjustmentId}` | `ADJUSTMENT:ADJ-1` |

#### `BalanceQueryService.java` — Cached read path

```java
package com.gepe.gepay.ledger.internal.service;

import com.gepe.gepay.ledger.api.dto.AccountBalance;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import com.gepe.gepay.ledger.internal.exception.LedgerError;
import com.gepe.gepay.ledger.internal.mapper.LedgerMapper;
import com.gepe.gepay.ledger.internal.repository.AccountRepository;
import com.gepe.gepay.platform.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4log
@Service
@RequiredArgsConstructor
public class BalanceQueryService {

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final LedgerMapper mapper;

    @Cacheable(cacheNames = "ledger.accountBalance", key = "#code.code + ':' + #ownerRef")
    @Transactional(readOnly = true)
    public AccountBalance getBalance(AccountCode code, String ownerRef) {
        AccountOwnerType ownerType = code.getOwnerType();
        return accountRepository.findByCodeAndOwnerTypeAndOwnerRef(code.getCode(), ownerType, ownerRef)
                .map(mapper::toBalance)
                .orElseThrow(() -> new ServiceException(LedgerError.ACCOUNT_NOT_FOUND, code.getCode(), ownerRef));
    }

    @Transactional(readOnly = true)
    public long getBalanceAmount(AccountCode code, String ownerRef) {
        return getBalance(code, ownerRef).balance();
    }

    // Called by LedgerService after balance mutation
    @CacheEvict(cacheNames = "ledger.accountBalance", key = "#account.code + ':' + #account.ownerRef")
    public void evictBalance(Account account) {
        log.debug("Evicted balance cache for account: code={}, ownerRef={}", account.getCode(), account.getOwnerRef());
    }
}
```

### 3.3 Mapper

```java
// LedgerMapper.java
package com.gepe.gepay.ledger.internal.mapper;

import com.gepe.gepay.ledger.api.dto.AccountBalance;
import com.gepe.gepay.ledger.internal.entity.Account;
import com.gepe.gepay.ledger.api.enums.AccountCode;
import com.gepe.gepay.ledger.api.enums.AccountOwnerType;
import com.gepe.gepay.ledger.api.enums.AccountType;
import com.gepe.gepay.ledger.api.enums.EntryDirection;
import org.springframework.stereotype.Component;

@Component
public class LedgerMapper {

    public AccountBalance toBalance(Account account) {
        AccountCode code = AccountCode.fromCode(account.getCode());
        return new AccountBalance(
                account.getId(),
                code,
                account.getName(),
                account.getType(),
                account.getNormalBalance(),
                account.getOwnerType() != null ? account.getOwnerType().name() : null,
                account.getOwnerRef(),
                account.getCurrency(),
                account.getBalance(),
                account.isActive()
        );
    }
}
```

### 3.4 Exception / Error Codes

```java
// LedgerError.java
package com.gepe.gepay.ledger.internal.exception;

import com.gepe.gepay.platform.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LedgerError implements ErrorCode {

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "ledger.account_not_found"),
    UNBALANCED_JOURNAL(HttpStatus.BAD_REQUEST, "ledger.unbalanced_journal"),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "ledger.idempotency_conflict"),
    JOURNAL_MIN_LINES(HttpStatus.BAD_REQUEST, "ledger.journal_min_lines"),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "ledger.invalid_amount"),
    REVERSES_JOURNAL_NOT_FOUND(HttpStatus.NOT_FOUND, "ledger.reverses_journal_not_found"),
    ACCOUNT_INACTIVE(HttpStatus.CONFLICT, "ledger.account_inactive"),
    CONCURRENT_BALANCE_UPDATE(HttpStatus.CONFLICT, "ledger.concurrent_balance_update"),
    ACCOUNT_CODE_INVALID(HttpStatus.BAD_REQUEST, "ledger.account_code_invalid"),
    OWNER_REF_REQUIRED(HttpStatus.BAD_REQUEST, "ledger.owner_ref_required");

    private final HttpStatus httpStatus;
    private final String messageKey;

    LedgerError(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus getHttpStatus() { return httpStatus; }
    @Override public String getMessageKey() { return messageKey; }
}
```

### 3.5 Cache Configuration

```java
// LedgerCacheConfig.java
package com.gepe.gepay.ledger.internal.config;

import com.gepe.gepay.ledger.api.dto.AccountBalance;
import com.gepe.gepay.platform.config.CacheConfig;
import com.gepe.gepay.platform.config.CacheSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerCacheConfig {

    @Bean
    public CacheSpec accountBalanceCacheSpec() {
        return CacheSpec.builder()
                .name("ledger.accountBalance")
                .valueType(AccountBalance.class)
                .ttlSeconds(300)  // 5 minutes TTL — safety net for cross-instance eviction
                .build();
    }
}
```

---

## 4. Module Configuration (i18n)

### `src/main/resources/i18n/ledger/messages.properties`

```properties
# Ledger module messages (English - default)
ledger.account_not_found=Account with code {0} and owner {1} was not found
ledger.unbalanced_journal=Journal is unbalanced: debit={0}, credit={1}
ledger.idempotency_conflict=Journal with idempotency key {0} already exists
ledger.journal_min_lines=Journal must have at least 2 lines (debit and credit)
ledger.invalid_amount=Invalid amount for account {0}: must be positive
ledger.reverses_journal_not_found=Reverses journal with id {0} not found
ledger.account_inactive=Account {0} is inactive
ledger.concurrent_balance_update=Concurrent balance update detected, please retry
ledger.account_code_invalid=Invalid account code: {0}
ledger.owner_ref_required=Owner reference is required for account code {0}
```

### `src/main/resources/i18n/ledger/messages_id.properties`

```properties
# Ledger module messages (Indonesian)
ledger.account_not_found=Akun dengan kode {0} dan pemilik {1} tidak ditemukan
ledger.unbalanced_journal=Jurnal tidak seimbang: debit={0}, kredit={1}
ledger.idempotency_conflict=Jurnal dengan kunci idempotensi {0} sudah ada
ledger.journal_min_lines=Jurnal minimal harus memiliki 2 baris (debit dan kredit)
ledger.invalid_amount=Jumlah tidak valid untuk akun {0}: harus positif
ledger.reverses_journal_not_found=Jurnal pembalikan dengan id {0} tidak ditemukan
ledger.account_inactive=Akun {0} tidak aktif
ledger.concurrent_balance_update=Terjadi pembaruan saldo bersamaan, silakan coba lagi
ledger.account_code_invalid=Kode akun tidak valid: {0}
ledger.owner_ref_required=Referensi pemilik wajib diisi untuk kode akun {0}
```

---

## 5. Usage by Payment Module (Reference)

The `payment` module will call `LedgerApi` like this — **exact journal examples from `design.md` & `example.md`**:

```java
// ============================================================
// 1. PAYMENT PAID — design.md §7.2, example.md §2.3 (J-1)
// ============================================================
// DEBIT   1100 PG Clearing Receivable (Midtrans)   100.000
// CREDIT  2100 Creator Payable - Pending            93.340
// CREDIT  4000 Platform Fee Revenue                  6.000
// CREDIT  2300 VAT Payable                             660

List<JournalLine> paidLines = List.of(
    new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, midtransProviderId, EntryDirection.DEBIT, 100_000),
    new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, creatorUserId, EntryDirection.CREDIT, 93_340),
    new JournalLine(AccountCode.PLATFORM_FEE_REVENUE, null, EntryDirection.CREDIT, 6_000),
    new JournalLine(AccountCode.VAT_PAYABLE, null, EntryDirection.CREDIT, 660)
);

ledgerApi.postJournal(
    "PAYMENT:" + paymentId + ":PAID",    // idempotency_key format
    JournalReferenceType.PAYMENT,
    paymentId,                            // reference_id = paymentId
    "Donation " + paymentId + " paid at Midtrans",
    paidAt,                               // occurred_at = webhook timestamp
    paidLines,
    null
);

// ============================================================
// 2. SETTLEMENT CONFIRMED — design.md §7.3, example.md §3.2 (J-2)
// ============================================================
// DEBIT   1150 Payin Provider Balance (Midtrans)   100.000
// CREDIT  1100 PG Clearing Receivable (Midtrans)   100.000

List<JournalLine> settleLines = List.of(
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, midtransProviderId, EntryDirection.DEBIT, 100_000),
    new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, midtransProviderId, EntryDirection.CREDIT, 100_000)
);

ledgerApi.postJournal(
    "SETTLEMENT:" + settlementId + ":CONFIRMED",
    JournalReferenceType.SETTLEMENT,
    settlementId,
    "Settlement " + settlementId + " confirmed for payment " + paymentId,
    settledAt,
    settleLines,
    null
);

// ============================================================
// 3. RELEASE CREATOR FUNDS — design.md §7.3, example.md §3.3 (J-3)
// ============================================================
// DEBIT   2100 Creator Payable - Pending           93.340
// CREDIT  2110 Creator Payable - Available         93.340

List<JournalLine> releaseLines = List.of(
    new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, creatorUserId, EntryDirection.DEBIT, 93_340),
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, creatorUserId, EntryDirection.CREDIT, 93_340)
);

ledgerApi.postJournal(
    "SETTLEMENT:" + settlementId + ":RELEASE",
    JournalReferenceType.SETTLEMENT,
    settlementId,
    "Release creator funds for settlement " + settlementId,
    settledAt,
    releaseLines,
    null
);

// ============================================================
// 4. WITHDRAWAL HOLD — design.md §8.1, example.md §5.3 (J-4)
// ============================================================
// DEBIT   2110 Creator Payable - Available         500.000
// CREDIT  2200 Withdrawal Payable                  500.000

List<JournalLine> holdLines = List.of(
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, creatorUserId, EntryDirection.DEBIT, 500_000),
    new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, creatorUserId, EntryDirection.CREDIT, 500_000)
);

ledgerApi.postJournal(
    "WITHDRAWAL:" + withdrawalId + ":HOLD",
    JournalReferenceType.WITHDRAWAL,
    withdrawalId,
    "Hold withdrawal " + withdrawalId,
    requestedAt,
    holdLines,
    null
);

// ============================================================
// 5. FUND TRANSFER (Midtrans → Flip) — design.md §9.2, example.md §5.4 (J-5)
// ============================================================
// DEBIT   1300 Payout Provider Float (Flip)      503.000
// CREDIT  1150 Payin Provider Balance (Midtrans)   503.000

List<JournalLine> fundTransferLines = List.of(
    new JournalLine(AccountCode.PAYOUT_PROVIDER_FLOAT, flipProviderId, EntryDirection.DEBIT, 503_000),
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, midtransProviderId, EntryDirection.CREDIT, 503_000)
);

ledgerApi.postJournal(
    "FUND_TRANSFER:" + fundTransferId,
    JournalReferenceType.FUND_TRANSFER,
    fundTransferId,
    "Top-up Flip " + fundTransferId + " from Midtrans balance",
    transferAt,
    fundTransferLines,
    null
);

// ============================================================
// 6. PAYOUT COMPLETED — design.md §8.3, example.md §5.5 (J-6)
// ============================================================
// DEBIT   2200 Withdrawal Payable                  500.000
// DEBIT   5100 Payout Fee Expense                    3.000
// CREDIT  4100 Withdrawal Fee Revenue                2.500
// CREDIT  1300 Payout Provider Float               500.500

List<JournalLine> payoutLines = List.of(
    new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, creatorUserId, EntryDirection.DEBIT, 500_000),
    new JournalLine(AccountCode.PAYOUT_FEE_EXPENSE, null, EntryDirection.DEBIT, 3_000),
    new JournalLine(AccountCode.WITHDRAWAL_FEE_REVENUE, null, EntryDirection.CREDIT, 2_500),
    new JournalLine(AccountCode.PAYOUT_PROVIDER_FLOAT, flipProviderId, EntryDirection.CREDIT, 500_500)
);

ledgerApi.postJournal(
    "PAYOUT:" + payoutId + ":COMPLETED",
    JournalReferenceType.PAYOUT,
    payoutId,
    "Payout " + payoutId + " completed for withdrawal " + withdrawalId,
    completedAt,
    payoutLines,
    null
);

// ============================================================
// 7. REFUND AFTER SETTLEMENT — design.md §10.2, example.md §7.2 (J-11)
// ============================================================
// DEBIT   2110 Creator Payable - Available         93.340
// DEBIT   4000 Platform Fee Revenue                 6.000
// DEBIT   2300 VAT Payable                            660
// CREDIT  1150 Payin Provider Balance              100.000

List<JournalLine> refundLines = List.of(
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, creatorUserId, EntryDirection.DEBIT, 93_340),
    new JournalLine(AccountCode.PLATFORM_FEE_REVENUE, null, EntryDirection.DEBIT, 6_000),
    new JournalLine(AccountCode.VAT_PAYABLE, null, EntryDirection.DEBIT, 660),
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, midtransProviderId, EntryDirection.CREDIT, 100_000)
);

ledgerApi.postJournal(
    "REFUND:" + refundId,
    JournalReferenceType.REFUND,
    refundId,
    "Refund " + refundId + " for payment " + paymentId,
    refundAt,
    refundLines,
    originalJournalId  // reverses_journal_id
);
```

---

## 6. Testing Requirements

### 6.1 Unit Tests
- `AccountServiceTest` — getOrCreate creates lazily, idempotent on concurrent calls
- `LedgerServiceTest` — postJournal validates balance, updates accounts, idempotent replay
- `JournalValidatorTest` — rejects unbalanced, <2 lines, negative amounts
- `BalanceQueryServiceTest` — cache hit/miss, eviction after mutation

### 6.2 Integration Tests (need Postgres)
- Full journal posting with real DB
- Concurrent `getOrCreateAccount` for same (code, owner) — only one created
- Optimistic lock on `Account.version` when two journals update same account
- **Concurrent `postJournal` with overlapping accounts in different line orders — no deadlock, both succeed**
- **Idempotency race: two threads same idempotencyKey — exactly one journal created**

### 6.3 Modularity Test
- `ModularityTests` must pass (already exists, verifies `ledger` boundaries)

---

## 7. Migration Checklist

- [ ] V4 migration already applied (tables + global accounts seed)
- [ ] V4 includes unique index on `journals.idempotency_key` (idempotency constraint)
- [ ] V4 includes composite index on `accounts(code, owner_type, owner_ref)` (for batched lock ordering)
- [ ] No new migrations needed for ledger module (entities match V4)
- [ ] Payment module will need its own migrations (V5 already exists)

---

## 8. Implementation Order (Suggested)

| Step | File(s) | Description |
|------|---------|-------------|
| 1 | `LedgerError`, `messages.properties`, `messages_id.properties` | Error codes + i18n (foundation) |
| 2 | `LedgerApi`, DTO records | Contract definition |
| 3 | `AccountRepository`, `JournalRepository`, `EntryRepository` | Data access |
| 4 | `AccountService` (getOrCreate) | Lazy account creation |
| 5 | `JournalValidator` | Balance validation |
| 6 | `LedgerService.postJournal` | Core write logic |
| 7 | `BalanceQueryService` + `LedgerCacheConfig` | Cached read path |
| 8 | `LedgerMapper` | Entity ↔ DTO |
| 9 | Unit + integration tests | Verify behavior |
| 10 | `./mvnw test` | ModularityTests + all tests green |

---

## 9. Key Conventions to Follow

| Rule | Reference |
|------|-----------|
| `api` never depends on `internal` | `../../AGENTS.md` §2.1 |
| DTOs = `record`, Entities = Lombok `@Getter` `@Setter` | `../../AGENTS.md` §3 |
| UUID v7 for user-facing IDs, BIGINT identity for internal | `../../AGENTS.md` §3 |
| `@Transactional` on service, not repository/controller | `../../AGENTS.md` §3 |
| Cache via `CacheSpec` in `internal/config`, TTL = safety net | `../../AGENTS.md` §3, §11.2 |
| i18n keys prefixed with `ledger.` | `../../AGENTS.md` §6 |
| Error enum implements `ErrorCode`, throw `ServiceException` | `../../AGENTS.md` §6 |
| Idempotency via `journals.idempotency_key` unique constraint | `design.md` §12.4 |
| **Deadlock prevention: deterministic lock order + batched FOR UPDATE** | **This doc §Deadlock Prevention** |
| Double-entry: `SUM(DEBIT) = SUM(CREDIT)` enforced in service | `design.md` §12.1 |
| Append-only journals, corrections via new journal + `reverses_journal_id` | `design.md` §12.2-3 |

---

## 10. Quick Reference: AccountCode Enum (Source of Truth)

All account metadata (code, displayName, type, normalBalance, ownerType) comes from `AccountCode` enum — **single source of truth**.

| AccountCode | Code | Display Name | Type | Normal | OwnerType | Created |
|-------------|------|--------------|------|--------|-----------|---------|
| `PG_CLEARING_RECEIVABLE` | 1100 | PG Clearing Receivable | ASSET | DEBIT | PAYMENT_PROVIDER | Lazy (first PAID) |
| `PAYIN_PROVIDER_BALANCE` | 1150 | Payin Provider Balance | ASSET | DEBIT | PAYMENT_PROVIDER | Lazy (first CONFIRMED) |
| `BANK_OPERATING` | 1200 | Bank Operating | ASSET | DEBIT | BANK | **V4 Seeded** (BANK-1) |
| `PAYOUT_PROVIDER_FLOAT` | 1300 | Payout Provider Float | ASSET | DEBIT | PAYOUT_PROVIDER | Lazy (first TO_PAYOUT_PROVIDER) |
| `FUND_TRANSFER_IN_TRANSIT` | 1400 | Fund Transfer In Transit | ASSET | DEBIT | null | **V4 Seeded** (global) |
| `CREATOR_PAYABLE_PENDING` | 2100 | Creator Payable - Pending | LIABILITY | CREDIT | USER | Lazy (first PAID) |
| `CREATOR_PAYABLE_AVAILABLE` | 2110 | Creator Payable - Available | LIABILITY | CREDIT | USER | Lazy (first RELEASE) |
| `WITHDRAWAL_PAYABLE` | 2200 | Withdrawal Payable | LIABILITY | CREDIT | USER | Lazy (first HOLD) |
| `VAT_PAYABLE` | 2300 | VAT Payable | LIABILITY | CREDIT | null | **V4 Seeded** (global) |
| `PLATFORM_FEE_REVENUE` | 4000 | Platform Fee Revenue | REVENUE | CREDIT | null | **V4 Seeded** (global) |
| `WITHDRAWAL_FEE_REVENUE` | 4100 | Withdrawal Fee Revenue | REVENUE | CREDIT | null | **V4 Seeded** (global) |
| `PAYMENT_GATEWAY_FEE_EXPENSE` | 5000 | Payment Gateway Fee Expense | EXPENSE | DEBIT | null | **V4 Seeded** (global) |
| `PAYOUT_FEE_EXPENSE` | 5100 | Payout Fee Expense | EXPENSE | DEBIT | null | **V4 Seeded** (global) |
| `BANK_TRANSFER_FEE_EXPENSE` | 5150 | Bank / Fund Transfer Fee Expense | EXPENSE | DEBIT | null | **V4 Seeded** (global) |
| `REFUND_CHARGEBACK_LOSS` | 5200 | Refund / Chargeback Loss | EXPENSE | DEBIT | null | **V4 Seeded** (global) |
| `CREATOR_NEGATIVE_BALANCE` | 5300 | Creator Negative Balance | ASSET | DEBIT | USER | Lazy (first clawback) |
| `FUND_TRANSFER_VARIANCE` | 5900 | Fund Transfer Variance | EXPENSE | DEBIT | null | **V4 Seeded** (global) |

**Key methods on `AccountCode`:**
```java
code.getCode()                    // "1100"
code.getDisplayName()             // "PG Clearing Receivable"
code.getType()                    // AccountType.ASSET
code.normalBalance()              // EntryDirection.DEBIT
code.getOwnerType()               // AccountOwnerType.PAYMENT_PROVIDER (or null)
code.isGlobal()                   // true if ownerType == null
AccountCode.fromCode("1100")      // returns PG_CLEARING_RECEIVABLE
```

---

## 11. Quick Reference: JournalReferenceType Enum

| Value | Business Event | Example referenceId |
|-------|----------------|---------------------|
| `PAYMENT` | Payment paid/refunded | `PAY-1` (paymentId) |
| `SETTLEMENT` | Settlement confirmed/release | `SET-1` (settlementId) |
| `WITHDRAWAL` | Withdrawal hold | `WD-1` (withdrawalId) |
| `PAYOUT` | Payout completed/failed | `PO-1` (payoutId) |
| `FUND_TRANSFER` | Top-up between providers | `FT-1` (fundTransferId) |
| `REFUND` | Refund processed | `RF-1` (refundId) |
| `ADJUSTMENT` | Manual adjustment | `ADJ-1` (adjustmentId) |