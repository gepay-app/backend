# Payment — Konsep & Alur

> Modul `payment` adalah **mesin pembayaran generik**: menangani pembayaran masuk
> (payin), settlement, tarik dana (withdrawal), dan payout. Ia **tidak tahu aturan
> bisnis per produk** — donasi / pembelian konten hanyalah sebuah **kode `type`**
> yang dipanggil oleh modul consumer (mis. modul `donation` nanti). Payment juga
> **tidak menulis jurnal sendiri**; semua angka uang dicatat `ledger` lewat `LedgerApi`.
>
> Istilah yang membingungkan (terutama **"settlement"**): [Glosarium](../glossary.md).
> Akun & jurnal: [Ledger](../ledger/overview.md).
>
> Urutan dokumen payment:
> 1. **konsep & alur** — dokumen ini
> 2. [Status](./states.md) — semua state machine
> 3. [Settlement otomatis](./settlement.md) — job Quartz
> 4. [Contoh lengkap](./example.md) — angka per langkah
> 5. [Rencana kerja](./todo.md) — langkah implementasi

---

## 1. Prinsip inti

| Prinsip | Bahasa manusia |
|---|---|
| **Vendor-blind** | Kode bisnis tidak menyebut Midtrans/Flip. Bahasa vendor diterjemahkan **adapter**. |
| **Domain-agnostic** | Payment tidak tahu aturan donasi/konten. Tiap "produk" hanyalah kode `type` (`String`); modul consumer (mis. `donation`) yang memanggil payment. |
| **Payment ≠ ledger** | Payment menyusun "gerakan apa"; ledger mencatat "saldo siapa berubah berapa". |
| **Idempotency dari pemanggil** | Payment membuat `idempotencyKey` (`TIPE:ID:AKSI`) agar webhook dobel tidak posting dua kali. |
| **Settlement otomatis (portofolio)** | Jurnal settlement diposting job Quartz berbasis T+n; di produksi pemicunya wajib bukti. |

> **Consumer vs engine.** `payment` adalah engine. **Donasi/pembelian konten adalah
> consumer** (modul terpisah, mis. `donation`) yang nanti memanggil `payment::api`.
> Karena itu `payment` tidak menyimpan aturan donasi — hanya kode `type` (`String`).
> Fee platform per produk pun disimpan sebagai data di `fee_configs.product_type`,
> bukan sebagai enum domain.

---

## 2. Kata "settlement" punya 3 arti

| # | Arti | Sumber | Di sistem kita |
|---|---|---|---|
| A | Status di Midtrans (`transaction_status=settlement`): uang sudah masuk **saldo Midtrans** | webhook/CSV Midtrans | `payments.status = PAID` |
| B | **Withdrawable di PG**: saldo PG boleh ditarik ke bank | dashboard MAP | tidak ada kolom, cuma pantauan |
| C | **Settlement kita**: uang dianggap cair ke bank | job Quartz (T+n hari kerja) | batch `settlements` → J-2 + J-3 |

Midtrans pakai "settlement" untuk **A**; kita pakai **settled** untuk **C**.
Jangan disamakan.

---

## 3. Hak creator: Pending vs Available

- **Pending** = creator sudah berhak, tapi uang belum cair → **belum boleh ditarik**.
- **Available** = uang sudah cair → **boleh ditarik**.
- **Hold** (`WITHDRAWAL_PAYABLE`) = sedang ditarik, uang dikunci agar tidak dobel.

Perpindahan Pending → Available terjadi saat settlement dikonfirmasi (J-3).

---

## 4. Alur uang

```
Pembayar → PG (VA/QRIS) → webhook PAID (J-1: piutang)
   → batch settlement otomatis (job Quartz, T+n) → CONFIRMED
        J-2: PG_CLEARING_RECEIVABLE → BANK_OPERATING
        J-3: creator PENDING → AVAILABLE
   → fund transfer bank → Flip (top-up)                      [J-4]
   → withdrawal HOLD (available → withdrawal payable)        [J-5]
   → payout via Flip → COMPLETED (hold lunas + fee)           [J-6]
```

Angka lengkap per langkah: [example.md](./example.md).

---

## 5. Koneksi ke `ledger`

Payment menyusun jurnal, ledger yang memposting. Ringkasan event → jurnal → key:

| Kejadian | Jurnal | Idempotency key | Angka (contoh) |
|---|---|---|---|
| Pembayaran dibayar (payin) | J-1 | `PAYMENT:{paymentId}:PAID` | debit piutang 100.000; kredit pending creator + fee + PPN |
| Settlement dikonfirmasi | J-2 | `SETTLEMENT:{id}:CONFIRMED` | bank 100.000 ← piutang |
| Release hak creator | J-3 | `SETTLEMENT:{id}:RELEASE:{userId}` | pending 93.340 → available |
| Fund transfer | J-4 | `FUND_TRANSFER:{id}` | float Flip ← bank |
| Withdrawal hold | J-5 | `WITHDRAWAL:{id}:HOLD` | available → withdrawal payable |
| Payout selesai | J-6 | `PAYOUT:{id}:COMPLETED` | withdrawal payable → float Flip + fee |
| Payout gagal | J-7 | `PAYOUT:{id}:FAILED` | withdrawal payable → available |
| Refund | J-8/J-9 | `REFUND:{id}` | reversal J-1 |
| Adjustment | J-10 | `ADJUSTMENT:{id}` | variance |
| Chargeback | J-11 | (pakai `REFUND`/`ADJUSTMENT`) | kerugian + clawback |

Detail isi jurnal: [Ledger — Contoh](../ledger/examples.md).

---

## 6. Aturan

- **Fee PG ditanggung pembayar.** Charge memakai `gross_amount = gross + pg_fee`;
  `expected_settlement_amount = gross`. Fee PG **tidak masuk ledger** — hanya untuk
  rekonsiliasi, karena pembayar yang membayarnya.
- **Webhook** boleh `permitAll`, tapi **wajib verifikasi signature** — jangan percaya
  payload mentah.
- **Tanpa `@Scheduled`** — pakai Quartz (aman multi-instance).
- Hari libur nasional di-maintain di `payment.holidays` tiap tahun.
- `order_id` Midtrans = `payment_attempts.id` (UUID v7) — kunci matching 1:1.

---

## 7. Entitas utama (`V5__payment_tables.sql`)

| Tabel | Fungsi |
|---|---|
| `providers` | daftar penyedia: MIDTRANS (payin), FLIP (payout) |
| `channels` / `channel_routes` | metode bayar netral + kebijakan settlement per route (T+n, target) |
| `holidays` | hari libur nasional untuk hitung hari kerja |
| `fee_configs` / `user_fee_overrides` | tarif ber-versi + tarif khusus user |
| `payments` / `payment_attempts` | pembayaran generik (punya `type`) + percobaan bayar (snapshot fee) |
| `settlements` | batch pencairan PG → bank (otomatis job Quartz) |
| `refunds`, `withdrawals`, `payout_destinations`, `payouts`, `fund_transfers` | refund, tarik dana, payout |
| `adjustments` | koreksi manual (maker-checker) |
| `processed_events` | inbox idempotency webhook |
| `reconciliation_runs` / `_items` | rekonsiliasi (fase lanjut) |
