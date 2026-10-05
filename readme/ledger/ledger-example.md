# Ledger Module — Journal Examples

> Pendamping [`ledger-design.md`](./ledger-design.md). Semua jurnal contoh menggunakan `AccountCode` enum asli.  
> Nominal dalam IDR (rupiah bulat, `BIGINT`).

---

## Legend

| AccountCode | Kode | Normal | OwnerType |
|-------------|------|--------|-----------|
| `PG_CLEARING_RECEIVABLE` | 1100 | DEBIT | PAYMENT_PROVIDER |
| `PAYIN_PROVIDER_BALANCE` | 1150 | DEBIT | PAYMENT_PROVIDER |
| `BANK_OPERATING` | 1200 | DEBIT | BANK |
| `PAYOUT_PROVIDER_FLOAT` | 1300 | DEBIT | PAYOUT_PROVIDER |
| `FUND_TRANSFER_IN_TRANSIT` | 1400 | DEBIT | null |
| `CREATOR_PAYABLE_PENDING` | 2100 | CREDIT | USER |
| `CREATOR_PAYABLE_AVAILABLE` | 2110 | CREDIT | USER |
| `WITHDRAWAL_PAYABLE` | 2200 | CREDIT | USER |
| `VAT_PAYABLE` | 2300 | CREDIT | null |
| `PLATFORM_FEE_REVENUE` | 4000 | CREDIT | null |
| `WITHDRAWAL_FEE_REVENUE` | 4100 | CREDIT | null |
| `PG_FEE_EXPENSE` | 5000 | DEBIT | null |
| `PAYOUT_FEE_EXPENSE` | 5100 | DEBIT | null |
| `BANK_TRANSFER_FEE_EXPENSE` | 5150 | DEBIT | null |
| `REFUND_CHARGEBACK_LOSS` | 5200 | DEBIT | null |
| `CREATOR_NEGATIVE_BALANCE` | 5300 | DEBIT | USER |
| `FUND_TRANSFER_VARIANCE` | 5900 | DEBIT | null |

---

## J-1: Payment Paid (Donasi Masuk)

**Kasus**: Donasi Rp 100.000 via Midtrans ke Creator `USER-123`.  
Platform fee 6% (6.000), PPN 11% dari fee (660), Net creator 93.340.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `PG_CLEARING_RECEIVABLE` | `MIDTRANS` | DEBIT | 100.000 |
| 2 | `CREATOR_PAYABLE_PENDING` | `USER-123` | CREDIT | 93.340 |
| 3 | `PLATFORM_FEE_REVENUE` | `null` | CREDIT | 6.000 |
| 4 | `VAT_PAYABLE` | `null` | CREDIT | 660 |

**Σ DEBIT = 100.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.DEBIT, 100_000),
    new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, "USER-123", EntryDirection.CREDIT, 93_340),
    new JournalLine(AccountCode.PLATFORM_FEE_REVENUE, null, EntryDirection.CREDIT, 6_000),
    new JournalLine(AccountCode.VAT_PAYABLE, null, EntryDirection.CREDIT, 660)
);

ledgerApi.postJournal("PAYMENT:PAY-1:PAID", JournalReferenceType.PAYMENT, "PAY-1",
    "Donation PAY-1 paid via Midtrans", paidAt, lines, null);
```

---

## J-2: Settlement Confirmed (PG → Bank Platform)

**Kasus**: Midtrans settlement Rp 100.000 terkonfirmasi (laporan diterima).

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `PAYIN_PROVIDER_BALANCE` | `MIDTRANS` | DEBIT | 100.000 |
| 2 | `PG_CLEARING_RECEIVABLE` | `MIDTRANS` | CREDIT | 100.000 |

**Σ DEBIT = 100.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, "MIDTRANS", EntryDirection.DEBIT, 100_000),
    new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS", EntryDirection.CREDIT, 100_000)
);

ledgerApi.postJournal("SETTLEMENT:SET-1:CONFIRMED", JournalReferenceType.SETTLEMENT, "SET-1",
    "Settlement SET-1 confirmed for payment PAY-1", settledAt, lines, null);
```

---

## J-3: Release Creator Funds (Pending → Available)

**Kasus**: Pelepasan dana creator setelah settlement terkonfirmasi.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `CREATOR_PAYABLE_PENDING` | `USER-123` | DEBIT | 93.340 |
| 2 | `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | CREDIT | 93.340 |

**Σ DEBIT = 93.340 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, "USER-123", EntryDirection.DEBIT, 93_340),
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, "USER-123", EntryDirection.CREDIT, 93_340)
);

ledgerApi.postJournal("SETTLEMENT:SET-1:RELEASE", JournalReferenceType.SETTLEMENT, "SET-1",
    "Release creator funds for settlement SET-1", settledAt, lines, null);
```

---

## J-4: Withdrawal Hold (Available → Hold)

**Kasus**: Creator `USER-123` request withdraw Rp 500.000.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | DEBIT | 500.000 |
| 2 | `WITHDRAWAL_PAYABLE` | `USER-123` | CREDIT | 500.000 |

**Σ DEBIT = 500.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, "USER-123", EntryDirection.DEBIT, 500_000),
    new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, "USER-123", EntryDirection.CREDIT, 500_000)
);

ledgerApi.postJournal("WITHDRAWAL:WD-1:HOLD", JournalReferenceType.WITHDRAWAL, "WD-1",
    "Hold withdrawal WD-1", requestedAt, lines, null);
```

---

## J-5: Fund Transfer (Midtrans → Flip)

**Kasus**: Top-up Flip Rp 503.000 dari saldo Midtrans (termasuk buffer fee).

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `PAYOUT_PROVIDER_FLOAT` | `FLIP` | DEBIT | 503.000 |
| 2 | `PAYIN_PROVIDER_BALANCE` | `MIDTRANS` | CREDIT | 503.000 |

**Σ DEBIT = 503.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.PAYOUT_PROVIDER_FLOAT, "FLIP", EntryDirection.DEBIT, 503_000),
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, "MIDTRANS", EntryDirection.CREDIT, 503_000)
);

ledgerApi.postJournal("FUND_TRANSFER:FT-1", JournalReferenceType.FUND_TRANSFER, "FT-1",
    "Top-up Flip FT-1 from Midtrans balance", transferAt, lines, null);
```

---

## J-6: Payout Completed (Flip → Creator Bank)

**Kasus**: Payout Rp 500.000 ke rekening creator. Fee Flip Rp 3.000, platform ambil fee Rp 2.500.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `WITHDRAWAL_PAYABLE` | `USER-123` | DEBIT | 500.000 |
| 2 | `PAYOUT_FEE_EXPENSE` | `null` | DEBIT | 3.000 |
| 3 | `WITHDRAWAL_FEE_REVENUE` | `null` | CREDIT | 2.500 |
| 4 | `PAYOUT_PROVIDER_FLOAT` | `FLIP` | CREDIT | 500.500 |

**Σ DEBIT = 503.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, "USER-123", EntryDirection.DEBIT, 500_000),
    new JournalLine(AccountCode.PAYOUT_FEE_EXPENSE, null, EntryDirection.DEBIT, 3_000),
    new JournalLine(AccountCode.WITHDRAWAL_FEE_REVENUE, null, EntryDirection.CREDIT, 2_500),
    new JournalLine(AccountCode.PAYOUT_PROVIDER_FLOAT, "FLIP", EntryDirection.CREDIT, 500_500)
);

ledgerApi.postJournal("PAYOUT:PO-1:COMPLETED", JournalReferenceType.PAYOUT, "PO-1",
    "Payout PO-1 completed for withdrawal WD-1", completedAt, lines, null);
```

---

## J-7: Payout Failed (Rollback Hold)

**Kasus**: Payout gagal, kembalikan dana ke available.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `WITHDRAWAL_PAYABLE` | `USER-123` | DEBIT | 500.000 |
| 2 | `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | CREDIT | 500.000 |

**Σ DEBIT = 500.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.WITHDRAWAL_PAYABLE, "USER-123", EntryDirection.DEBIT, 500_000),
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, "USER-123", EntryDirection.CREDIT, 500_000)
);

ledgerApi.postJournal("PAYOUT:PO-1:FAILED", JournalReferenceType.PAYOUT, "PO-1",
    "Payout PO-1 failed, rollback hold", failedAt, lines, null);
```

---

## J-8: Refund After Settlement (Reversal)

**Kasus**: Refund donasi PAY-1 setelah settlement. Reversal jurnal J-1.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | DEBIT | 93.340 |
| 2 | `PLATFORM_FEE_REVENUE` | `null` | DEBIT | 6.000 |
| 3 | `VAT_PAYABLE` | `null` | DEBIT | 660 |
| 4 | `PAYIN_PROVIDER_BALANCE` | `MIDTRANS` | CREDIT | 100.000 |

**Σ DEBIT = 100.000 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, "USER-123", EntryDirection.DEBIT, 93_340),
    new JournalLine(AccountCode.PLATFORM_FEE_REVENUE, null, EntryDirection.DEBIT, 6_000),
    new JournalLine(AccountCode.VAT_PAYABLE, null, EntryDirection.DEBIT, 660),
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, "MIDTRANS", EntryDirection.CREDIT, 100_000)
);

ledgerApi.postJournal("REFUND:RF-1", JournalReferenceType.REFUND, "RF-1",
    "Refund RF-1 for payment PAY-1", refundAt, lines, originalJournalId); // reversesJournalId = J-1
```

---

## J-9: Refund Before Settlement (Only Pending)

**Kasus**: Refund sebelum settlement (creator belum release). Hanya pending yang dibalik.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `CREATOR_PAYABLE_PENDING` | `USER-123` | DEBIT | 93.340 |
| 2 | `PLATFORM_FEE_REVENUE` | `null` | DEBIT | 6.000 |
| 3 | `VAT_PAYABLE` | `null` | DEBIT | 660 |
| 4 | `PG_CLEARING_RECEIVABLE` | `MIDTRANS` | CREDIT | 100.000 |

**Σ DEBIT = 100.000 = Σ CREDIT** ✓

---

## J-10: Adjustment (Koreksi Manual)

**Kasus**: Selisih Rp 500 ditemukan saat rekonsiliasi, dibukukan ke variance.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `FUND_TRANSFER_VARIANCE` | `null` | DEBIT | 500 |
| 2 | `PAYIN_PROVIDER_BALANCE` | `MIDTRANS` | CREDIT | 500 |

**Σ DEBIT = 500 = Σ CREDIT** ✓

```java
List<JournalLine> lines = List.of(
    new JournalLine(AccountCode.FUND_TRANSFER_VARIANCE, null, EntryDirection.DEBIT, 500),
    new JournalLine(AccountCode.PAYIN_PROVIDER_BALANCE, "MIDTRANS", EntryDirection.CREDIT, 500)
);

ledgerApi.postJournal("ADJUSTMENT:ADJ-1", JournalReferenceType.ADJUSTMENT, "ADJ-1",
    "Adjustment for reconciliation variance MIDTRANS", adjustedAt, lines, null);
```

---

## J-11: Chargeback (Kerugian)

**Kasus**: Chargeback dari Midtrans Rp 100.000 (sudah settle). Creator sudah withdraw.

| # | AccountCode | OwnerRef | Direction | Amount |
|---|-------------|----------|-----------|--------|
| 1 | `REFUND_CHARGEBACK_LOSS` | `null` | DEBIT | 100.000 |
| 2 | `PAYIN_PROVIDER_BALANCE` | `MIDTRANS` | CREDIT | 100.000 |
| 3 | `CREATOR_NEGATIVE_BALANCE` | `USER-123` | DEBIT | 93.340 |
| 4 | `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | CREDIT | 93.340 |

**Σ DEBIT = 193.340 = Σ CREDIT** ✓

---

## Query Balance Examples

```java
// Saldo siap tarik creator
long available = ledgerApi.getBalanceAmount(AccountCode.CREATOR_PAYABLE_AVAILABLE, "USER-123");

// Saldo pending creator
long pending = ledgerApi.getBalanceAmount(AccountCode.CREATOR_PAYABLE_PENDING, "USER-123");

// Piutang ke Midtrans
long receivable = ledgerApi.getBalanceAmount(AccountCode.PG_CLEARING_RECEIVABLE, "MIDTRANS");

// Saldo bank operasional
long bank = ledgerApi.getBalanceAmount(AccountCode.BANK_OPERATING, "BANK-1");

// Total revenue platform
long revenue = ledgerApi.getBalanceAmount(AccountCode.PLATFORM_FEE_REVENUE, null);
```
