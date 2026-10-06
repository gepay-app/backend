# Ledger Module — As-Built Implementation

> Dokumen ini **mengikuti kode yang ada** (`com.gepe.gepay.ledger`), bukan rencana.
> Konvensi: [`../../AGENTS.md`](../../AGENTS.md) — `api`/`internal` split,
> `@NamedInterface("api")`, module i18n, `ErrorCode` + `ServiceException`,
> `ModularityTests`.
>
> Untuk contoh jurnal lengkap lihat [`ledger-example.md`](./ledger-example.md);
> konsep & kontrak di [`ledger-overview.md`](./ledger_overview.md) /
> [`ledger-design.md`](./ledger-design.md).

---

## 1. Struktur File (aktual)

```
src/main/java/com/gepe/gepay/ledger/
├── package-info.java                    @ApplicationModule(id="ledger")  — CLOSED
├── api/
│   ├── package-info.java                @NamedInterface("api")
│   ├── LedgerApi.java                   facade interface
│   ├── dtos/
│   │   ├── AccountDto.java              record (bukan "AccountBalance")
│   │   ├── JournalLine.java             record
│   │   ├── PostJournalRequest.java      record (belum dipakai)
│   │   └── PostJournalResult.java       record
│   └── enums/
│       ├── AccountCode.java             sumber kebenaran Chart of Accounts
│       ├── AccountOwnerType.java        USER | PAYMENT_PROVIDER | PAYOUT_PROVIDER | BANK
│       ├── AccountType.java             ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
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
    │   └── LedgerMapper.java            Account -> AccountDto (method: toDto)
    ├── repository/
    │   ├── AccountRepository.java
    │   ├── EntryRepository.java
    │   └── JournalRepository.java
    ├── service/
    │   ├── AccountProvisioningService.java  INSERT akun di REQUIRES_NEW (race-safe)
    │   ├── AccountService.java          lazy get-or-create + read path
    │   ├── LedgerApiImpl.java           implementasi LedgerApi (delegasi)
    │   └── LedgerService.java           core posting
    └── validator/
        └── JournalValidator.java        ΣDEBIT = ΣCREDIT
```

Tidak ada (belum diimplementasikan): `internal/config` (cache), `internal/delivery/http`,
`internal/listener`, `BalanceQueryService`, `LedgerEvent`.

---

## 2. Kontrak `LedgerApi`

`com.gepe.gepay.ledger.api.LedgerApi` — **hanya satu** method tulis; `idempotencyKey`
wajib dari caller (lihat `ledger_overview.md` §2). Tidak ada overload convenience di api.

```java
public interface LedgerApi {
    AccountDto getOrCreateAccount(AccountCode code, String ownerRef);
    AccountDto getAccount(AccountCode code, String ownerRef);

    PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId       // nullable
    );

    AccountDto getBalance(AccountCode code, String ownerRef);
    long getBalanceAmount(AccountCode code, String ownerRef);
}
```

> `LedgerService` punya overload internal `postJournal(referenceType, referenceId, …)`
> yang menurunkan key dari `description` (`TYPE:refId:hex(description.hashCode())`).
> Ini **tidak** diekspos di api dan hanya untuk internal/test.

### DTO (`api/dtos`)

```java
public record JournalLine(AccountCode accountCode, String ownerRef,
                          EntryDirection direction, long amount) {}

public record AccountDto(long id, AccountCode code, String name, AccountType type,
                         EntryDirection normalBalance, String ownerType, String ownerRef,
                         String currency, long balance, boolean active) {}

public record PostJournalResult(long journalId, String idempotencyKey, boolean newlyCreated) {}

// ada tapi belum dipakai
public record PostJournalRequest(String idempotencyKey, JournalReferenceType referenceType,
        String referenceId, String description, Instant occurredAt,
        List<JournalLine> lines, Long reversesJournalId) {}
```

---

## 3. Chart of Accounts (`AccountCode`)

`AccountCode` adalah **sumber kebenaran**. Metadata `type`, `normalBalance`, dan
`ownerType` diturunkan dari enum — service tidak bisa salah mengisi.

- Konstruktor: `AccountCode(code, displayName, type, ownerType)`.
- `normalBalance()` = `type.getNormalBalance()` (ASSET/EXPENSE → DEBIT; LIABILITY/EQUITY/REVENUE → CREDIT).
- `isGlobal()` = `ownerType == null`.
- Disimpan di DB sebagai kode (`'1100'`) lewat `AccountCode.JpaConverter`.
- `fromCode(String)` fail-fast kalau kode tak dikenal; startup gagal kalau ada kode ganda.

| AccountCode | Kode | Type | Normal | OwnerType |
|-------------|------|------|--------|-----------|
| `PG_CLEARING_RECEIVABLE` | 1100 | ASSET | DEBIT | PAYMENT_PROVIDER |
| `PAYIN_PROVIDER_BALANCE` | 1150 | ASSET | DEBIT | PAYMENT_PROVIDER |
| `BANK_OPERATING` | 1200 | ASSET | DEBIT | BANK |
| `PAYOUT_PROVIDER_FLOAT` | 1300 | ASSET | DEBIT | PAYOUT_PROVIDER |
| `FUND_TRANSFER_IN_TRANSIT` | 1400 | ASSET | DEBIT | null |
| `CREATOR_PAYABLE_PENDING` | 2100 | LIABILITY | CREDIT | USER |
| `CREATOR_PAYABLE_AVAILABLE` | 2110 | LIABILITY | CREDIT | USER |
| `WITHDRAWAL_PAYABLE` | 2200 | LIABILITY | CREDIT | USER |
| `VAT_PAYABLE` | 2300 | LIABILITY | CREDIT | null |
| `PLATFORM_FEE_REVENUE` | 4000 | REVENUE | CREDIT | null |
| `WITHDRAWAL_FEE_REVENUE` | 4100 | REVENUE | CREDIT | null |
| `PG_FEE_EXPENSE` | 5000 | EXPENSE | DEBIT | null |
| `PAYOUT_FEE_EXPENSE` | 5100 | EXPENSE | DEBIT | null |
| `BANK_TRANSFER_FEE_EXPENSE` | 5150 | EXPENSE | DEBIT | null |
| `REFUND_CHARGEBACK_LOSS` | 5200 | EXPENSE | DEBIT | null |
| `CREATOR_NEGATIVE_BALANCE` | 5300 | ASSET | DEBIT | USER |
| `FUND_TRANSFER_VARIANCE` | 5900 | EXPENSE | DEBIT | null |

> Nama enum di kode adalah **`PG_FEE_EXPENSE`** (bukan
> `PAYMENT_GATEWAY_FEE_EXPENSE`). Akun global + `BANK-1` di-seed oleh
> `V4__ledger_tables.sql`; akun per-owner dibuat lazy.

---

## 4. `AccountService` — lazy creation & read path

```java
@Transactional
AccountDto getOrCreateAccount(AccountCode code, String ownerRef) {
    if (code == null) -> ACCOUNT_CODE_INVALID
    if (code.getOwnerType() != null && ownerRef blank) -> OWNER_REF_REQUIRED
    if (code.isGlobal() && ownerRef non-blank) ownerRef = null;

    // fast path: non-locking read
    findByCodeAndOwnerTypeAndOwnerRef(...) -> toDto

    // belum ada: INSERT di transaksi TERPISAH (REQUIRES_NEW) lewat
    // AccountProvisioningService.insert(...) — unique constraint
    // (code, owner_type, owner_ref) yang jaga race. Karena transaksi dalam
    // punya sendiri, kalau kalah race hanya transaksi itu yang di-abort
    // Postgres; transaksi pemanggil tetap hidup dan bisa re-read pemenang.
    try { provisioningService.insert(Account.create(code, ownerRef)); }
    catch (DataIntegrityViolationException) { re-read pemenang; }
}

@Transactional(readOnly = true)
AccountDto getAccount(AccountCode code, String ownerRef)      // NotFound -> ACCOUNT_NOT_FOUND
AccountDto getBalance(AccountCode code, String ownerRef)      // alias getAccount
long getBalanceAmount(AccountCode code, String ownerRef)
```

Read path **selalu membaca langsung dari DB**. Ledger **sengaja tidak di-cache**
(pengecualian sadar terhadap `docs/agents/caching.md` §3): saldo adalah data
keuangan krusial dan tidak boleh disajikan basi.

`LedgerApiImpl` hanya mendelegasikan tiap method api ke `AccountService` /
`LedgerService`.

---

## 5. `LedgerService.postJournal` — langkah eksekusi

Seluruh method `@Transactional`:

1. **Idempotency pre-check** — `journalRepository.findByIdempotencyKey(key)`; kalau
   ada → kembalikan `PostJournalResult(id, key, newlyCreated=false)`.
2. **Validasi** — `JournalValidator.validate(lines)` (≥2 baris, amount > 0, balanced).
3. **Resolve/get-or-create akun** tanpa lock (distinct per `(accountCode, ownerRef)`),
   lalu ambil entity via `accountRepository.findById`.
4. **Sort id ascending + batch lock** — `accountRepository.lockAllByIds(sortedIds)`
   (`SELECT ... FOR UPDATE ORDER BY array_position`). Kunci diambil sekali, urutan
   global sama untuk semua transaksi → bebas deadlock.
5. **Buat Journal**; kalau `reversesJournalId != null`:
   - `REVERSES_JOURNAL_NOT_FOUND` kalau jurnal asal tidak ada;
   - `JOURNAL_ALREADY_REVERSED` kalau `existsByReversesJournalId(...)`.
   `saveAndFlush`; `DataIntegrityViolationException` = race idempotency → re-read → hasil replay.
6. **Buat `Entry` + mutasi saldo** — tiap baris `lockedAcc.apply(direction, amount)`;
   `ACCOUNT_INACTIVE` kalau akun non-aktif. Lalu `entryRepository.saveAll(...)` dan
   `accountRepository.saveAll(lockedAccountMap.values())` (memicu increment `@Version`).

Urutan langkah sama seperti `ledger-design.md` §7.

---

## 6. Repositories

```java
// AccountRepository (entity Account)
Optional<Account> findByIdForUpdate(Long id);                       // PESSIMISTIC_WRITE
Optional<Account> findByCodeAndOwnerTypeAndOwnerRef(
        AccountCode code, AccountOwnerType ownerType, String ownerRef);
@Lock(PESSIMISTIC_WRITE)
@Query(nativeQuery = true, value = """
        SELECT * FROM ledger.accounts
        WHERE id = ANY(:ids) ORDER BY array_position(:ids::bigint[], id) FOR UPDATE""")
List<Account> lockAllByIds(List<Long> ids);
List<Account> findByOwnerTypeAndOwnerRef(AccountOwnerType ownerType, String ownerRef);
List<Account> findByOwnerTypeIsNull();

// JournalRepository
Optional<Journal> findByIdempotencyKey(String idempotencyKey);
boolean existsByReversesJournalId(Long reversesJournalId);
List<Journal> findByReferenceTypeAndReferenceId(...);
List<Journal> findByReferenceTypeAndReferenceIdOrderByIdDesc(...);

// EntryRepository
List<Entry> findByJournalId(Long journalId);
List<Entry> findByAccountIdOrderById(Long accountId);
Long computeBalance(Long accountId);   // SUM(DEBIT) - SUM(CREDIT)
```

---

## 7. Validasi, Error, i18n

`JournalValidator.validate(lines)` melempar `ServiceException`:
`JOURNAL_MIN_LINES` (null/<2), `INVALID_AMOUNT` (≤0), `UNBALANCED_JOURNAL` (Σdebit≠Σkredit).

`LedgerError implements ErrorCode`:

| Error | HTTP | message key |
|-------|------|-------------|
| `ACCOUNT_NOT_FOUND` | 404 | `ledger.account_not_found` |
| `UNBALANCED_JOURNAL` | 400 | `ledger.unbalanced_journal` |
| `IDEMPOTENCY_CONFLICT` | 409 | `ledger.idempotency_conflict` |
| `JOURNAL_MIN_LINES` | 400 | `ledger.journal_min_lines` |
| `INVALID_AMOUNT` | 400 | `ledger.invalid_amount` |
| `REVERSES_JOURNAL_NOT_FOUND` | 404 | `ledger.reverses_journal_not_found` |
| `JOURNAL_ALREADY_REVERSED` | 409 | `ledger.journal_already_reversed` |
| `ACCOUNT_INACTIVE` | 409 | `ledger.account_inactive` |
| `CONCURRENT_BALANCE_UPDATE` | 409 | `ledger.concurrent_balance_update` |
| `ACCOUNT_CODE_INVALID` | 400 | `ledger.account_code_invalid` |
| `OWNER_REF_REQUIRED` | 400 | `ledger.owner_ref_required` |

i18n: `src/main/resources/i18n/ledger/messages.properties` (English) +
`messages_id.properties` (Indonesia) — key berprefix `ledger.`.

---

## 8. Configuration

- **Package**: `com.gepe.gepay.ledger`; `@ApplicationModule(id="ledger")` (CLOSED, tanpa `allowedDependencies`).
- **API**: `com.gepe.gepay.ledger.api` (`@NamedInterface("api")`).
- **Cache**: sengaja tidak di-cache (saldo = data krusial, selalu dari DB).
- **Migration**: `V4__ledger_tables.sql` (schema `ledger` + seed akun global & `BANK-1`).
- Persistensi enum sebagai `varchar`; akun dikunci batch dengan `FOR UPDATE`.

---

## 9. Cara `payment` memakai ledger

Payment **tidak menulis jurnal langsung**; ia menyusun `JournalLine` + `idempotencyKey`
lalu memanggil `LedgerApi.postJournal(...)`. Ringkasan event → key:

| Event | Idempotency key | Jurnal (lihat `ledger-example.md`) |
|-------|-----------------|------------------------------------|
| Payment paid | `PAYMENT:{paymentId}:PAID` | J-1 |
| Settlement confirmed | `SETTLEMENT:{settlementId}:CONFIRMED` | J-2 |
| Release creator funds | `SETTLEMENT:{settlementId}:RELEASE` | J-3 |
| Fund transfer | `FUND_TRANSFER:{fundTransferId}` | J-4 |
| Withdrawal hold | `WITHDRAWAL:{withdrawalId}:HOLD` | J-5 |
| Payout completed | `PAYOUT:{payoutId}:COMPLETED` | J-6 |
| Payout failed | `PAYOUT:{payoutId}:FAILED` | J-7 |
| Refund | `REFUND:{refundId}` | J-8/J-9 |
| Adjustment | `ADJUSTMENT:{adjustmentId}` | J-10 |
| Chargeback | `CHARGEBACK:{...}` | J-11 |

> `JournalReferenceType` saat ini **tidak punya** `CHARGEBACK`; jurnal chargeback
> diposting sebagai `REFUND` atau `ADJUSTMENT` (lihat catatan di `todo.md`).

---

## 10. Test

Sudah ada (`src/test/java/com/gepe/gepay/ledger/`):

- `AccountServiceTest` — lazy create, race winner, ownerRef/global validation, getAccount not found.
- `LedgerServiceTest` — idempotent replay, lock order sorted, saldo ter-update.
- `JournalValidatorTest` — <2 baris, unbalanced, amount ≤ 0.
- `ModularityTests` — batas modul tetap hijau.

Yang **belum** ada: test reversal (`JOURNAL_ALREADY_REVERSED`) dan integration
test race `getOrCreateAccount` dengan 2 thread nyata (unit test hanya mem-*mock*
`AccountProvisioningService`). Test cache tidak relevan — ledger sengaja tidak
di-cache.
