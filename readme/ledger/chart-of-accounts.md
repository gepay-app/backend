# Ledger — Daftar Akun (Chart of Accounts)

> Satu tabel untuk semua akun. Sumber kebenaran kode: enum
> `com.gepe.gepay.ledger.api.enums.AccountCode`. Tipe & saldo normal sudah menempel di
> enum, jadi service tidak bisa salah mengisinya.
>
> Cara baca: [Ledger — Konsep](./overview.md) · Contoh jurnal: [Ledger — Contoh](./examples.md).

---

## Tabel akun

| Kode | Nama (enum) | Tipe | Saldo normal | Owner | Penjelasan singkat |
|---|---|---|---|---|---|
| 1100 | `PG_CLEARING_RECEIVABLE` | Aset | Debit | provider payin | **Piutang ke PG.** Pembayar sudah bayar, tapi uang belum masuk bank kita. |
| 1150 | `PAYIN_PROVIDER_BALANCE` | Aset | Debit | provider payin | Uang kita yang masih "titip" di saldo provider payin (belum ditarik ke bank). |
| 1200 | `BANK_OPERATING` | Aset | Debit | bank (`BANK-1`) | Rekening bank operasional platform — uang riil kita. |
| 1300 | `PAYOUT_PROVIDER_FLOAT` | Aset | Debit | provider payout | Saldo yang kita titipkan di provider payout (Flip) untuk bayar creator. |
| 1400 | `FUND_TRANSFER_IN_TRANSIT` | Aset | Debit | global | Uang yang sedang dalam perjalanan antar rekening/provider. |
| 2100 | `CREATOR_PAYABLE_PENDING` | Liabilitas | Kredit | user | Hak creator yang **belum boleh ditarik** (menunggu masa settlement). |
| 2110 | `CREATOR_PAYABLE_AVAILABLE` | Liabilitas | Kredit | user | Hak creator yang **sudah boleh ditarik**. |
| 2200 | `WITHDRAWAL_PAYABLE` | Liabilitas | Kredit | user | Hak creator yang **sedang ditarik** (dikunci sementara). |
| 2300 | `VAT_PAYABLE` | Liabilitas | Kredit | global | PPN yang kita utang ke negara (11% dari fee platform). |
| 4000 | `PLATFORM_FEE_REVENUE` | Pendapatan | Kredit | global | Pendapatan fee platform dari transaksi. |
| 4100 | `WITHDRAWAL_FEE_REVENUE` | Pendapatan | Kredit | global | Pendapatan fee penarikan dana. |
| 5000 | `PG_FEE_EXPENSE` | Beban | Debit | global | Beban fee payment gateway (dipakai hanya kalau kita yang menanggung). |
| 5100 | `PAYOUT_FEE_EXPENSE` | Beban | Debit | global | Beban fee payout provider. |
| 5150 | `BANK_TRANSFER_FEE_EXPENSE` | Beban | Debit | global | Beban biaya transfer bank. |
| 5200 | `REFUND_CHARGEBACK_LOSS` | Beban | Debit | global | Kerugian akibat refund / chargeback. |
| 5300 | `CREATOR_NEGATIVE_BALANCE` | Aset | Debit | user | Piutang ke creator (clawback, mis. setelah chargeback dana sudah ditarik). |
| 5900 | `FUND_TRANSFER_VARIANCE` | Beban | Debit | global | Selisih tak terjelaskan saat transfer antar akun. |

---

## Cara membacanya

- **Aset** = yang kita miliki/hak kita → saldo tumbuh saat **debit**.
- **Liabilitas** = yang kita utang → saldo tumbuh saat **kredit**.
- **Pendapatan** = penghasilan kita → saldo tumbuh saat **kredit**.
- **Beban** = biaya kita → saldo tumbuh saat **debit**.

## Global vs per-owner

- **Global** (owner `null`): satu akun untuk semua, mis. `VAT_PAYABLE`,
  `PLATFORM_FEE_REVENUE`.
- **Per-owner**: satu akun per pemilik, mis. hak creator `USER-123` berbeda dengan
  `USER-456`. Dibuat otomatis saat pertama dipakai (*lazy*).

Akun global + `BANK-1` di-seed oleh `V4__ledger_tables.sql`. Akun per-owner dibuat
otomatis lewat `getOrCreateAccount` / `getBalance` dengan `ownerRef`
(mis. `USER-123`, `MIDTRANS`, `FLIP`).

> Di kode, nama enum beban fee PG adalah **`PG_FEE_EXPENSE`** (bukan
> `PAYMENT_GATEWAY_FEE_EXPENSE`).
