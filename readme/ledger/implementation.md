# Ledger — Implementasi (As-Built)

> Dokumen ini **mengikuti kode yang ada** (`com.gepe.gepay.ledger`), bukan rencana.
> Konsep & API: [overview.md](./overview.md) · Akun:
> [chart-of-accounts.md](./chart-of-accounts.md) · Contoh jurnal: [examples.md](./examples.md).

---

## 1. Struktur file

```
src/main/java/com/gepe/gepay/ledger/
├── package-info.java                    @ApplicationModule(id="ledger") — CLOSED
├── api/                                 @NamedInterface("api") — seluruh subtree
│   ├── package-info.java                @NamedInterface("api")
│   ├── LedgerApi.java                   facade interface
│   ├── dtos/
│   │   ├── package-info.java            @NamedInterface("api") (bagian dari "api")
│   │   ├── AccountDto.java              record
│   │   ├── JournalLine.java             record
│   │   ├── PostJournalRequest.java      record (belum dipakai)
│   │   └── PostJournalResult.java       record
│   └── enums/
│       ├── package-info.java            @NamedInterface("api") (bagian dari "api")
│       ├── AccountCode.java             sumber kebenaran Chart of Accounts
│       ├── AccountType.java             ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
│       ├── AccountOwnerType.java        USER | PAYMENT_PROVIDER | PAYOUT_PROVIDER | BANK
│       ├── EntryDirection.java          DEBIT | CREDIT
│       └── JournalReferenceType.java    PAYMENT | SETTLEMENT | WITHDRAWAL | PAYOUT
│                                        | FUND_TRANSFER | REFUND | ADJUSTMENT
└── internal/
    ├── entity/
    │   ├── Account.java                 saldo + @Version (optimistic lock)
    │   ├── Entry.java                   append-only (@Immutable)
    │   └── Journal.java                 header + reversesJournal
    ├── exception/
    │   └── LedgerError.java             implements ErrorCode
    ├── mapper/
    │   └── LedgerMapper.java            Account -> AccountDto
    ├── repository/
    │   ├── AccountRepository.java
    │   ├── EntryRepository.java
    │   └── JournalRepository.java
    ├── service/
    │   ├── AccountProvisioningService.java  INSERT akun di REQUIRES_NEW (race-safe)
    │   ├── AccountService.java          lazy get-or-create + read path
    │   ├── LedgerApiImpl.java           implementasi LedgerApi
    │   └── LedgerService.java           core posting
    └── validator/
        └── JournalValidator.java        ΣDEBIT = ΣKREDIT
```

Belum ada: `internal/config` (cache), `internal/delivery/http`, `internal/listener`,
`BalanceQueryService`, `LedgerEvent`.

---

## 2. `AccountService` — lazy creation & read path

```java
@Transactional
AccountDto getOrCreateAccount(AccountCode code, String ownerRef) {
    if (code == null) -> ACCOUNT_CODE_INVALID
    if (code.getOwnerType() != null && ownerRef blank) -> OWNER_REF_REQUIRED
    if (code.isGlobal() && ownerRef non-blank) ownerRef = null;

    findByCodeAndOwnerTypeAndOwnerRef(...) -> toDto   // fast path, tanpa lock

    // belum ada: INSERT di transaksi TERPISAH (REQUIRES_NEW) lewat
    // AccountProvisioningService.insert(...). Unique constraint
    // (code, owner_type, owner_ref) yang menjaga race; kalau kalah race,
    // hanya transaksi itu yang di-abort, pemanggil tetap hidup dan re-read pemenang.
}

@Transactional(readOnly = true)
AccountDto getAccount(AccountCode code, String ownerRef)     // NotFound -> ACCOUNT_NOT_FOUND
AccountDto getBalance(AccountCode code, String ownerRef)      // alias getAccount
long getBalanceAmount(AccountCode code, String ownerRef)
```

Read path **selalu** membaca DB. Ledger **sengaja tidak di-cache** (pengecualian sadar
terhadap `docs/agents/caching.md` §3): saldo adalah data keuangan krusial.

`LedgerApiImpl` hanya mendelegasikan tiap method api ke `AccountService` / `LedgerService`.

---

## 3. `LedgerService.postJournal` — langkah eksekusi

Seluruh method `@Transactional`:

1. **Idempotency pre-check** — cari `idempotency_key`; kalau ada, kembalikan hasil lama
   (`newlyCreated=false`).
2. **Validasi** — `JournalValidator.validate(lines)` (≥2 baris, amount > 0, seimbang).
3. **Resolve/get-or-create akun** tanpa lock (distinct per `(accountCode, ownerRef)`).
4. **Sort id ascending + batch lock** — `lockAllByIds(sortedIds)`
   (`SELECT ... FOR UPDATE ORDER BY array_position`). Urutan global sama untuk semua
   transaksi → bebas deadlock.
5. **Buat Journal**; kalau `reversesJournalId != null`: cek jurnal asal ada dan belum
   pernah dibalik. `saveAndFlush`; `DataIntegrityViolationException` = race idempotency →
   re-read → hasil replay.
6. **Buat Entry + mutasi saldo** — `apply(direction, amount)` tiap akun; simpan entry &
   akun (memicu increment `@Version`).

---

## 4. Repositories (ringkas)

```java
// AccountRepository
Optional<Account> findByIdForUpdate(Long id);
List<Account> lockAllByIds(List<Long> ids);   // native, ORDER BY array_position ... FOR UPDATE

// JournalRepository
Optional<Journal> findByIdempotencyKey(String key);
boolean existsByReversesJournalId(Long reversesJournalId);

// EntryRepository
List<Entry> findByJournalId(Long journalId);
Long computeBalance(Long accountId);          // SUM(DEBIT) - SUM(CREDIT)
```

---

## 5. Validasi, error, i18n

`JournalValidator.validate(lines)` melempar `ServiceException`: `JOURNAL_MIN_LINES`,
`INVALID_AMOUNT`, `UNBALANCED_JOURNAL`. Daftar `LedgerError` & HTTP ada di
[overview.md §2](./overview.md). i18n: `i18n/ledger/messages*.properties` (prefix
`ledger.`).

---

## 6. Test

Sudah ada di `src/test/java/com/gepe/gepay/ledger/`:

- `AccountServiceTest` — lazy create, race winner, validasi ownerRef/global, not found.
- `LedgerServiceTest` — idempotent replay, lock order, saldo ter-update.
- `JournalValidatorTest` — <2 baris, unbalanced, amount ≤ 0.
- `ModularityTests` — batas modul tetap hijau.

Belum ada: test reversal (`JOURNAL_ALREADY_REVERSED`) dan integration test race
`getOrCreateAccount` dengan thread nyata.
