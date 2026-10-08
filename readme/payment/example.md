# Payment — Contoh Alur Lengkap

> Satu perjalanan uang: pengguna bayar → settlement → creator tarik → payout, lengkap
> dengan status dan jurnal. Konsep: [overview.md](./overview.md) · Jurnal:
> [Ledger — Contoh](../ledger/examples.md).
>
> Contoh di sini memakai `type=DONATION`; `payment` sendiri hanya **engine
> generik** — aturan donasi dimiliki consumer terpisah.

---

## Asumsi

- Pembayaran **Rp 100.000** (`type=DONATION`) via Midtrans VA BCA, creator `USER-123`.
- Platform fee 6% → 6.000; PPN 11% dari fee → 660. Net creator = **93.340**.
- Fee PG VA BCA 4.440 **ditanggung pembayar**; pembayar bayar 104.440, kita terima gross
  100.000. Fee PG tidak masuk ledger.
- `settlement_delay_days = 3` (3 hari kerja), `settlement_target = BANK`.
- ID singkat: `PAY-1`, `ATT-1`, `SET-1`, `WD-1`, `PO-1`, `FT-1`. Jurnal `J-1`..`J-6`.

---

## Step 0 — Buat pembayaran

DB: `payments` (status `PENDING`, semua fee di-snapshot) + `payment_attempts`
(`provider_reference_id='ATT-1'`, `expires_at = now()+1h`). Belum ada jurnal.

## Step 1 — Webhook PAID

Adapter memetakan status Midtrans `settlement` → event `PAYMENT_PAID`.
`payments.status='PAID'`, `paid_at` diisi, `expected_settlement_date = paid_at + 3 hari kerja`.

**J-1** (`PAYMENT:PAY-1:PAID`):

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `PG_CLEARING_RECEIVABLE` | MIDTRANS | Debit | 100.000 |
| `CREATOR_PAYABLE_PENDING` | USER-123 | Kredit | 93.340 |
| `PLATFORM_FEE_REVENUE` | – | Kredit | 6.000 |
| `VAT_PAYABLE` | – | Kredit | 660 |

## Step 2 — Settlement otomatis

Job Quartz harian melihat `expected_settlement_date` sudah lewat, memilih `PAY-1`,
membuat + mengonfirmasi batch `SET-1` (`expected=actual=100.000`, `variance=0`), lalu
`payments.settlement_id='SET-1'`.

**J-2** (`SETTLEMENT:SET-1:CONFIRMED`):

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `BANK_OPERATING` | BANK-1 | Debit | 100.000 |
| `PG_CLEARING_RECEIVABLE` | MIDTRANS | Kredit | 100.000 |

**J-3** (`SETTLEMENT:SET-1:RELEASE:USER-123`):

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_PENDING` | USER-123 | Debit | 93.340 |
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Kredit | 93.340 |

Sekarang creator punya saldo available **93.340**.

## Step 3 — Fund transfer bank → Flip

Top-up float Flip 500.500 dari bank.

**J-4** (`FUND_TRANSFER:FT-1`):

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `PAYOUT_PROVIDER_FLOAT` | FLIP | Debit | 500.500 |
| `BANK_OPERATING` | BANK-1 | Kredit | 500.500 |

## Step 4 — Creator tarik dana

Creator minta tarik 90.000 (fee 3.000, net diterima 87.000).

**J-5** (`WITHDRAWAL:WD-1:HOLD`):

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | Debit | 90.000 |
| `WITHDRAWAL_PAYABLE` | USER-123 | Kredit | 90.000 |

## Step 5 — Payout via Flip

Payout sukses (fee Flip 2.500).

**J-6** (`PAYOUT:PO-1:COMPLETED`):

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `WITHDRAWAL_PAYABLE` | USER-123 | Debit | 90.000 |
| `PAYOUT_FEE_EXPENSE` | – | Debit | 2.500 |
| `WITHDRAWAL_FEE_REVENUE` | – | Kredit | 3.000 |
| `PAYOUT_PROVIDER_FLOAT` | FLIP | Kredit | 89.500 |

---

## Ringkasan saldo (subset)

| Akun | Owner | Saldo akhir |
|---|---|---|
| `PG_CLEARING_RECEIVABLE` | MIDTRANS | 0 |
| `BANK_OPERATING` | BANK-1 | 100.000 − 500.500 = −400.500 |
| `PAYOUT_PROVIDER_FLOAT` | FLIP | 500.500 − 89.500 = 411.000 |
| `CREATOR_PAYABLE_PENDING` | USER-123 | 0 |
| `CREATOR_PAYABLE_AVAILABLE` | USER-123 | 93.340 − 90.000 = 3.340 |
| `WITHDRAWAL_PAYABLE` | USER-123 | 0 |
| `VAT_PAYABLE` | – | 660 |
| `PLATFORM_FEE_REVENUE` | – | 6.000 |
| `WITHDRAWAL_FEE_REVENUE` | – | 3.000 |
| `PAYOUT_FEE_EXPENSE` | – | 2.500 |

> Semua jurnal selalu seimbang. Ledger append-only — koreksi lewat jurnal reversal,
> bukan edit.
