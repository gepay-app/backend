# Ledger — Contoh Jurnal per Kejadian

> Angka nyata untuk setiap kejadian bisnis. Semua nominal IDR bulat (`BIGINT`).
> Semua jurnal **selalu seimbang** (Σdebit = Σkredit).
>
> Konsep: [Ledger — Konsep](./overview.md) · Akun: [Chart of Accounts](./chart-of-accounts.md) ·
> Alur uang lengkap: [Payment — Contoh](../payment/example.md).

OwnerRef contoh: `MIDTRANS`, `FLIP`, `BANK-1`, `USER-123`; `null` = akun global.

---

## J-1 — Pembayaran dibayar (payin) (`PAYMENT:{id}:PAID`)

Pembayaran Rp 100.000 via Midtrans (contoh: donasi), creator `USER-123`. Platform fee 6.000, PPN 660,
net creator 93.340.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `PG_CLEARING_RECEIVABLE` | MIDTRANS | Debit | 100.000 |
| `CREATOR_PAYABLE_PENDING` | USER-123 | Kredit | 93.340 |
| `PLATFORM_FEE_REVENUE` | – | Kredit | 6.000 |
| `VAT_PAYABLE` | – | Kredit | 660 |

Uang diterima PG (piutang), tapi hak creator masih *pending*.

---

## J-2 — Settlement dikonfirmasi (`SETTLEMENT:{id}:CONFIRMED`)

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `BANK_OPERATING` | BANK-1 | Debit | 100.000 |
| `PG_CLEARING_RECEIVABLE` | MIDTRANS | Kredit | 100.000 |

> Kalau `settlement_target = PROVIDER_BALANCE`, debit-nya `PAYIN_PROVIDER_BALANCE`
> (1150), bukan `BANK_OPERATING`.

---

## J-3 — Release hak creator (`SETTLEMENT:{id}:RELEASE:{userId}`)

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_PENDING` | USER-123 | Debit | 93.340 |
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Kredit | 93.340 |

Satu batch bisa memuat banyak creator → jurnal J-3 dibuat **per creator**.

---

## J-4 — Fund transfer bank → Flip (`FUND_TRANSFER:{id}`)

Top-up float Flip 503.000 dari bank. Bukan untung/rugi — hanya memindahkan uang kita.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `PAYOUT_PROVIDER_FLOAT` | FLIP | Debit | 503.000 |
| `BANK_OPERATING` | BANK-1 | Kredit | 503.000 |

---

## J-5 — Withdrawal hold (`WITHDRAWAL:{id}:HOLD`)

Creator `USER-123` minta tarik 500.000; uang dikunci agar tidak dipakai ganda.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Debit | 500.000 |
| `WITHDRAWAL_PAYABLE` | USER-123 | Kredit | 500.000 |

---

## J-6 — Payout berhasil (`PAYOUT:{id}:COMPLETED`)

Payout 500.000, fee Flip 3.000, fee platform 2.500.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `WITHDRAWAL_PAYABLE` | USER-123 | Debit | 500.000 |
| `PAYOUT_FEE_EXPENSE` | – | Debit | 3.000 |
| `WITHDRAWAL_FEE_REVENUE` | – | Kredit | 2.500 |
| `PAYOUT_PROVIDER_FLOAT` | FLIP | Kredit | 500.500 |

---

## J-7 — Payout gagal (`PAYOUT:{id}:FAILED`)

Hold dikembalikan ke *available*.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `WITHDRAWAL_PAYABLE` | USER-123 | Debit | 500.000 |
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Kredit | 500.000 |

---

## J-8 — Refund setelah settlement (`REFUND:{id}`)

Reversal J-1 ketika dana sudah cair.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Debit | 93.340 |
| `PLATFORM_FEE_REVENUE` | – | Debit | 6.000 |
| `VAT_PAYABLE` | – | Debit | 660 |
| `PAYIN_PROVIDER_BALANCE` | MIDTRANS | Kredit | 100.000 |

---

## J-9 — Refund sebelum settlement

Hanya `PENDING` yang dibalik.

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_PENDING` | USER-123 | Debit | 93.340 |
| `PLATFORM_FEE_REVENUE` | – | Debit | 6.000 |
| `VAT_PAYABLE` | – | Debit | 660 |
| `PG_CLEARING_RECEIVABLE` | MIDTRANS | Kredit | 100.000 |

---

## J-10 — Adjustment koreksi (`ADJUSTMENT:{id}`)

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `FUND_TRANSFER_VARIANCE` | – | Debit | 500 |
| `PAYIN_PROVIDER_BALANCE` | MIDTRANS | Kredit | 500 |

---

## J-11 — Chargeback

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `REFUND_CHARGEBACK_LOSS` | – | Debit | 100.000 |
| `PAYIN_PROVIDER_BALANCE` | MIDTRANS | Kredit | 100.000 |
| `CREATOR_NEGATIVE_BALANCE` | USER-123 | Debit | 93.340 |
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Kredit | 93.340 |

> `JournalReferenceType` saat ini **tidak punya** `CHARGEBACK`; jurnal chargeback
> diposting sebagai `REFUND` atau `ADJUSTMENT` (catat di kode saat implementasi).

---

## Contoh query saldo

```java
long available = ledgerApi.getBalanceAmount(AccountCode.CREATOR_PAYABLE_AVAILABLE, "USER-123");
long pending   = ledgerApi.getBalanceAmount(AccountCode.CREATOR_PAYABLE_PENDING,   "USER-123");
long piutang   = ledgerApi.getBalanceAmount(AccountCode.PG_CLEARING_RECEIVABLE,    "MIDTRANS");
long bank      = ledgerApi.getBalanceAmount(AccountCode.BANK_OPERATING,            "BANK-1");
long revenue   = ledgerApi.getBalanceAmount(AccountCode.PLATFORM_FEE_REVENUE,      null);
```
