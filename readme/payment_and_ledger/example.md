# GePay — Example: End-to-End Flow & Database State

> Pendamping [`design.md`](./design.md). Semua angka nyata, semua state database ditampilkan.
> Tujuan: membuktikan bahwa ledger **balance**, idempoten, dan bisa diaudit.

**Asumsi contoh:**

- Mata uang IDR, nominal `BIGINT` (rupiah bulat).
- ID disingkat supaya mudah dibaca (`PAY-1`, `ACC-1100`, ...). Di produksi: `UUID v7` (payment/settlement/withdrawal) &
  `BIGINT identity` (account/journal/channel/config).
- Ditulis **DEBIT/CREDIT** (singkatan: DEBIT = DR, CREDIT = CR).
- PG fee di- *pass-through* ke donatur (dibayar di atas nominal). Fee platform dipotong dari donasi.
- `ACC-xxxx` = baris di `ledger.accounts`; sub-account per owner.

### Legend ID (biar tidak lupa)

| Prefix / ID                                         | Artinya (tabel)                                                      |
|-----------------------------------------------------|----------------------------------------------------------------------|
| `MIDTRANS` / `Flip`                               | provider di `payment.providers`                                      |
| `BCA_VA` / `BANK_BCA`                               | channel di `payment.channels`                                        |
| `101 (MIDTRANS/BCA_VA)` / `201 (Flip/BANK_BCA)`   | route di `payment.channel_routes`                                    |
| `501 (GATEWAY_PROCESSING)` – `504 (PAYOUT)`         | baris `payment.fee_configs` (rate card)                              |
| `ACC-xxxx`                                          | akun di `ledger.accounts` (mis. `ACC-1150 (Payin Provider Balance)`) |
| `USER-CREATOR (Budi Creator)`                       | user penerima hak (creator)                                          |
| `USER-DONOR (Donatur)`                              | user pembayar (donatur)                                              |
| `USER-ADMIN (Admin Ops)` / `USER-FINANCE (Finance)` | admin pengaju / penyetuju adjustment (segregation of duty)           |
| `BANK-1 (Rekening Bank Platform)`                   | rekening bank platform (owner_ref akun `ACC-1200 (Bank Operating)`)  |
| `PAY-1 (Donasi 100.000)`                            | baris `payment.payments` (donasi)                                    |
| `ATT-1 (VA BCA via Midtrans)`                       | baris `payment.payment_attempts` (attempt VA)                        |
| `PE-1 (Webhook payment.paid)`                       | baris `payment.processed_events` (webhook masuk)                     |
| `SET-1 (Settlement donasi PAY-1)`                   | baris `payment.settlements` (settlement)                             |
| `RF-1`..`RF-4`                                      | baris `payment.refunds` (refund)                                     |
| `DEST-1 (Rekening BCA)`                             | baris `payment.payout_destinations` (rekening creator)               |
| `WD-1` / `WD-2 (Tarik dana)`                        | baris `payment.withdrawals` (permintaan tarik)                       |
| `FT-1`..`FT-4 (Top-up lintas akun)`                 | baris `payment.fund_transfers` (top-up lintas akun)                  |
| `PO-1` / `PO-2 (Payout ke creator)`                 | baris `payment.payouts` (eksekusi payout)                            |
| `ADJ-1 (Koreksi selisih)`                           | baris `payment.adjustments` (koreksi selisih)                        |
| `J-1`..`J-22 (Jurnal kejadian)`                     | baris `ledger.journals` (satu kejadian finansial)                    |
| `E-1`..`E-8`                                        | baris `ledger.entries` (baris DEBIT/CREDIT)                          |

---

## 0. Seed data

### 0.1 Provider, channel, routing

`payment.providers`

| id (provider) | code     | name     | supports_payin | supports_payout |
|---------------|----------|----------|----------------|-----------------|
| 1             | MIDTRANS | Midtrans | true           | false           |
| 2             | Flip   | Flip   | false          | true            |

`payment.channels`

| id (channel) | code     | display_name                 | type        | direction |
|--------------|----------|------------------------------|-------------|-----------|
| 11           | BCA_VA   | Transfer BCA Virtual Account | VA          | PAYIN     |
| 21           | BANK_BCA | Transfer Bank BCA            | PAYOUT_BANK | PAYOUT    |

`payment.channel_routes`

| id (route) | provider_id | channel_id | provider_channel_code | min_amount | max_amount | priority |
|------------|-------------|------------|-----------------------|------------|------------|----------|
| 101        | 1           | 11         | VA_BCA                | 10000      | 0          | 100      |
| 201        | 2           | 21         | BCA                   | 10000      | 0          | 100      |

### 0.2 Fee configs (berversi)

`payment.fee_configs`

| id (fee config) | fee_type            | provider_id | channel_id | fixed_amount | percentage_bps | vat_bps | effective_from |
|-----------------|---------------------|-------------|------------|--------------|----------------|---------|----------------|
| 501             | GATEWAY_PROCESSING  | 1           | 11         | 1.000        | 70             | 1100    | 2026-01-01     |
| 502             | PLATFORM_DONATION   | –           | –          | 1.000        | 500            | 1100    | 2026-01-01     |
| 503             | PLATFORM_WITHDRAWAL | –           | –          | 2.500        | 0              | 0       | 2026-01-01     |
| 504             | PAYOUT              | 2           | 21         | 3.000        | 0              | 0       | 2026-01-01     |

Tidak ada `user_fee_overrides` untuk contoh ini.

### 0.3 Chart of accounts (saldo awal 0)

`ledger.accounts`

| id (account) | code | name                             | type      | normal_balance | owner_type       | owner_ref    |
|--------------|------|----------------------------------|-----------|----------------|------------------|--------------|
| ACC-1100     | 1100 | PG Clearing Receivable           | ASSET     | DEBIT          | PAYMENT_PROVIDER | 1            |
| ACC-1150     | 1150 | Payin Provider Balance           | ASSET     | DEBIT          | PAYMENT_PROVIDER | 1            |
| ACC-1200     | 1200 | Bank Operating                   | ASSET     | DEBIT          | BANK             | BANK-1       |
| ACC-1300     | 1300 | Payout Provider Float            | ASSET     | DEBIT          | PAYOUT_PROVIDER  | 2            |
| ACC-1400     | 1400 | Fund Transfer In Transit         | ASSET     | DEBIT          | –                | –            |
| ACC-2100     | 2100 | Creator Payable — Pending        | LIABILITY | CREDIT         | USER             | USER-CREATOR |
| ACC-2110     | 2110 | Creator Payable — Available      | LIABILITY | CREDIT         | USER             | USER-CREATOR |
| ACC-2200     | 2200 | Withdrawal Payable               | LIABILITY | CREDIT         | USER             | USER-CREATOR |
| ACC-2300     | 2300 | VAT Payable                      | LIABILITY | CREDIT         | –                | –            |
| ACC-4000     | 4000 | Platform Fee Revenue             | REVENUE   | CREDIT         | –                | –            |
| ACC-4100     | 4100 | Withdrawal Fee Revenue           | REVENUE   | CREDIT         | –                | –            |
| ACC-5100     | 5100 | Payout Fee Expense               | EXPENSE   | DEBIT          | –                | –            |
| ACC-5150     | 5150 | Bank / Fund Transfer Fee Expense | EXPENSE   | DEBIT          | –                | –            |
| ACC-5300     | 5300 | Creator Negative Balance         | ASSET     | DEBIT          | USER             | USER-CREATOR |
| ACC-5900     | 5900 | Fund Transfer Variance           | EXPENSE   | DEBIT          | –                | –            |

---

## 1. Donasi dibuat (belum ada uang)

**Request:** creator `USER-CREATOR`, donor `USER-DONOR`, channel BCA_VA, donasi murni `100.000`.

### 1.1 Perhitungan fee

```
Gateway (FEE 501 (GATEWAY_PROCESSING), provider Midtrans + BCA_VA):
  fixed                                  =   1.000
  percentage 70 bps × 100.000            =     700
  subtotal                               =   1.700
  VAT 1.100 bps × 1.700                  =     187
  pg_fee_amount                          =   1.887

Platform (FEE 502 (PLATFORM_DONATION), tanpa override):
  fixed                                  =   1.000
  percentage 500 bps × 100.000           =   5.000
  subtotal                               =   6.000
  VAT 1.100 bps × 6.000                  =     660
  platform_fee_amount                    =   6.660

total_charged_amount  = 100.000 + 1.887          = 101.887
net_creator_amount    = 100.000 - 6.660          =  93.340
expected_settlement   = 100.000
```

### 1.2 `payment.payments`

| id (payment)           | type     | status  | user_id                  | payer_id             | gross_amount | gateway_fee_config_id    | platform_fee_config_id  | user_fee_override_id | pg_fee_amount | platform_fee_amount | total_charged_amount | net_creator_amount | expected_settlement_amount |
|------------------------|----------|---------|-----------------------------|----------------------|--------------|--------------------------|-------------------------|----------------------|---------------|---------------------|----------------------|--------------------|----------------------------|
| PAY-1 (Donasi 100.000) | DONATION | PENDING | USER-CREATOR (Budi Creator) | USER-DONOR (Donatur) | 100.000      | 501 (GATEWAY_PROCESSING) | 502 (PLATFORM_DONATION) | NULL                 | 1.887         | 6.660               | 101.887              | 93.340             | 100.000                    |

### 1.3 `payment.payment_attempts`

| id (attempt)                | payment_id             | channel_route_id      | provider_reference_id           | payment_reference_number | status  | expires_at       |
|-----------------------------|------------------------|-----------------------|---------------------------------|--------------------------|---------|------------------|
| ATT-1 (VA BCA via Midtrans) | PAY-1 (Donasi 100.000) | 101 (MIDTRANS/BCA_VA) | MID-ORDER-PAY1 (order Midtrans) | 88081234567890           | PENDING | 2026-10-03 10:00 |

### 1.4 Ledger

Belum ada journal. Semua saldo `ledger.accounts` = **0**.

---

## 2. Payment PAID (uang ditangkap PG, belum settle)

Webhook Midtrans `payment.paid` masuk.

### 2.1 `payment.processed_events` (idempotency inbox)

| id (event)                  | provider_id  | event_type   | external_event_id                | processed_at     |
|-----------------------------|--------------|--------------|----------------------------------|------------------|
| PE-1 (Webhook payment.paid) | 1 (Midtrans) | PAYMENT_PAID | MID-EVT-001 (event payment.paid) | 2026-10-01 10:05 |

### 2.2 Perubahan state

- `payment_attempts`: ATT-1 (VA BCA via Midtrans) → `status = PAID`
- `payments`: PAY-1 (Donasi 100.000) → `status = PAID`, `paid_at = 2026-10-01 10:05`
- `payment.settlements` baru:

| id (settlement)                 | payment_id             | provider_id  | status   | expected_amount | expected_settlement_date | settlement_target | actual_amount | external_settlement_id |
|---------------------------------|------------------------|--------------|----------|-----------------|--------------------------|-------------------|---------------|------------------------|
| SET-1 (Settlement donasi PAY-1) | PAY-1 (Donasi 100.000) | 1 (Midtrans) | EXPECTED | 100.000         | 2026-10-03               | PROVIDER_BALANCE  | NULL          | NULL                   |

### 2.3 Journal J-1 — `payment:PAY-1:paid`

`ledger.journals`

| id (journal)             | idempotency_key    | reference_type | reference_id           | description                     | occurred_at      |
|--------------------------|--------------------|----------------|------------------------|---------------------------------|------------------|
| J-1 (payment:PAY-1:paid) | payment:PAY-1:paid | PAYMENT        | PAY-1 (Donasi 100.000) | Donation PAY-1 paid at Midtrans | 2026-10-01 10:05 |

`ledger.entries`

| id (entry) | journal_id | account_id                           | direction | amount  |
|------------|------------|--------------------------------------|-----------|---------|
| E-1        | J-1        | ACC-1100 (PG Clearing Receivable)    | DEBIT     | 100.000 |
| E-2        | J-1        | ACC-2100 (Creator Payable — Pending) | CREDIT    | 93.340  |
| E-3        | J-1        | ACC-4000 (Platform Fee Revenue)      | CREDIT    | 6.000   |
| E-4        | J-1        | ACC-2300 (VAT Payable)               | CREDIT    | 660     |

Cek: DEBIT 100.000 = CREDIT (93.340 + 6.000 + 660) = 100.000 ✓

### 2.4 Saldo setelah J-1

| account                                | balance |
|----------------------------------------|---------|
| ACC-1100 (PG Clearing Receivable)      | 100.000 |
| ACC-1150 (Payin Provider Balance)      | 0       |
| ACC-1300 (Payout Provider Float)       | 0       |
| ACC-2100 (Creator Payable — Pending)   | 93.340  |
| ACC-2110 (Creator Payable — Available) | 0       |
| ACC-2200 (Withdrawal Payable)          | 0       |
| ACC-2300 (VAT Payable)                 | 660     |
| ACC-4000 (Platform Fee Revenue)        | 6.000   |
| ACC-4100 (Withdrawal Fee Revenue)      | 0       |
| ACC-5100 (Payout Fee Expense)          | 0       |
| ACC-5300 (Creator Negative Balance)    | 0       |

> Creator belum bisa withdraw: `ACC-2110 (Creator Payable — Available) = 0`. Hak-nya ada di
> `ACC-2100 (Creator Payable — Pending)`.

---

## 3. Settlement penuh: PAID → SETTLED

Laporan settlement Midtrans menyatakan PAY-1 cair ke **saldo akun Midtrans platform** (bukan ke rekening bank),
`actual_amount = 100.000`.

### 3.1 Update `payment.settlements`

| id (settlement)                 | status    | expected_amount | actual_amount | variance_amount | settlement_target | actual_settled_at | evidence_source                       | external_settlement_id                    | confirmed_at     |
|---------------------------------|-----------|-----------------|---------------|-----------------|-------------------|-------------------|---------------------------------------|-------------------------------------------|------------------|
| SET-1 (Settlement donasi PAY-1) | CONFIRMED | 100.000         | 100.000       | 0               | PROVIDER_BALANCE  | 2026-10-03 02:00  | REPORT_FILE (file laporan settlement) | MID-SETTLE-1001 (ref settlement Midtrans) | 2026-10-03 03:00 |

### 3.2 Journal J-2 — `settlement:SET-1:confirmed` (piutang → saldo PG)

| id (entry) | journal_id | account_id                        | direction | amount  |
|------------|------------|-----------------------------------|-----------|---------|
| E-5        | J-2        | ACC-1150 (Payin Provider Balance) | DEBIT     | 100.000 |
| E-6        | J-2        | ACC-1100 (PG Clearing Receivable) | CREDIT    | 100.000 |

### 3.3 Journal J-3 — `settlement:SET-1:release` (cairkan hak creator)

| id (entry) | journal_id | account_id                             | direction | amount |
|------------|------------|----------------------------------------|-----------|--------|
| E-7        | J-3        | ACC-2100 (Creator Payable — Pending)   | DEBIT     | 93.340 |
| E-8        | J-3        | ACC-2110 (Creator Payable — Available) | CREDIT    | 93.340 |

### 3.4 Saldo setelah J-2 + J-3

| account                                | balance    | keterangan                      |
|----------------------------------------|------------|---------------------------------|
| ACC-1100 (PG Clearing Receivable)      | 0          | piutang PG lunas                |
| ACC-1150 (Payin Provider Balance)      | 100.000    | **uang ada di saldo Midtrans**  |
| ACC-1300 (Payout Provider Float)       | 0          |                                 |
| ACC-2100 (Creator Payable — Pending)   | 0          | pending kosong                  |
| ACC-2110 (Creator Payable — Available) | **93.340** | **available, bisa di-withdraw** |
| ACC-2300 (VAT Payable)                 | 660        | PPN utang                       |
| ACC-4000 (Platform Fee Revenue)        | 6.000      | revenue                         |

### 3.5 Semua journal sejauh ini

| journal                          | debit total | credit total |
|----------------------------------|-------------|--------------|
| J-1 (payment:PAY-1:paid)         | 100.000     | 100.000      |
| J-2 (settlement:SET-1:confirmed) | 100.000     | 100.000      |
| J-3 (settlement:SET-1:release)   | 93.340      | 93.340       |

---

## 4. Ringkasan: donatur → paid → settled → available

| Langkah | Kejadian bisnis | Efek ledger                                                                                                                                                                    | Saldo creator        |
|---------|-----------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------|
| 1       | Donasi dibuat   | – (belum ada uang)                                                                                                                                                             | –                    |
| 2       | PAID (webhook)  | DEBIT ACC-1100 (PG Clearing Receivable) / CREDIT ACC-2100 (Creator Payable — Pending), ACC-4000 (Platform Fee Revenue), ACC-2300 (VAT Payable)                                 | Pending 93.340       |
| 3       | SETTLED (bukti) | DEBIT ACC-1150 (Payin Provider Balance) / CREDIT ACC-1100 (PG Clearing Receivable); DEBIT ACC-2100 (Creator Payable — Pending) / CREDIT ACC-2110 (Creator Payable — Available) | **Available 93.340** |

Uang operasional: ada di **saldo Midtrans (ACC-1150 (Payin Provider Balance))**, belum di bank.

---

## 5. Withdraw (sukses)

**State awal** (anggap hasil akumulasi donasi-donasi sebelumnya; akun lain di luar pembahasan):

| account                                | balance |
|----------------------------------------|---------|
| ACC-1150 (Payin Provider Balance)      | 503.000 |
| ACC-2110 (Creator Payable — Available) | 500.000 |

Withdraw `WD-1 (Tarik dana 500.000)`: requested `500.000`, withdrawal fee `2.500` (FEE 503 (PLATFORM_WITHDRAWAL)), net
disbursement `497.500` ke BANK_BCA.

### 5.1 `payment.payout_destinations`

| id (destination)      | user_id                  | channel_id    | account_number | account_name | bank_code               | is_default |
|-----------------------|-----------------------------|---------------|----------------|--------------|-------------------------|------------|
| DEST-1 (Rekening BCA) | USER-CREATOR (Budi Creator) | 21 (BANK_BCA) | 1234567890     | Budi Creator | BCA (Bank Central Asia) | true       |

### 5.2 `payment.withdrawals`

| id (withdrawal)           | user_id                  | destination_id        | channel_route_id      | status    | requested_amount | withdrawal_fee_amount | net_disbursement_amount |
|---------------------------|-----------------------------|-----------------------|-----------------------|-----------|------------------|-----------------------|-------------------------|
| WD-1 (Tarik dana 500.000) | USER-CREATOR (Budi Creator) | DEST-1 (Rekening BCA) | 201 (Flip/BANK_BCA) | REQUESTED | 500.000          | 2.500                 | 497.500                 |

### 5.3 Journal J-4 — `withdrawal:WD-1:hold`

| journal_id | account_id                             | direction | amount  |
|------------|----------------------------------------|-----------|---------|
| J-4        | ACC-2110 (Creator Payable — Available) | DEBIT     | 500.000 |
| J-4        | ACC-2200 (Withdrawal Payable)          | CREDIT    | 500.000 |

Saldo: `ACC-2110 (Creator Payable — Available) = 0`, `ACC-2200 (Withdrawal Payable) = 500.000`.

### 5.4 Funding ke float payout — `payment.fund_transfers`

Top-up ke Flip sebesar `503.000` (500.000 + payout fee 3.000, FEE 504 (PAYOUT)).

> **Catatan:** di sini funding **disederhanakan** (anggap saldo siap transfer) agar fokus ke mekanik withdrawal. Untuk
> alur lintas provider yang lengkap (Midtrans → bank → Flip, in-transit, biaya, adjustment), lihat **§8**.

| id (fund transfer)           | direction          | source_type      | target_type     | counterparty_provider_id | sent_amount | fee_amount | received_amount | variance_amount | status    | bank_reference | provider_reference            |
|------------------------------|--------------------|------------------|-----------------|--------------------------|-------------|------------|-----------------|-----------------|-----------|----------------|-------------------------------|
| FT-1 (Top-up Flip 503.000) | TO_PAYOUT_PROVIDER | PROVIDER_BALANCE | PAYOUT_PROVIDER | 2 (Flip)               | 503.000     | 0          | 503.000         | 0               | COMPLETED | –              | XND-TOPUP-7001 (topup Flip) |

Journal J-5 — `fund_transfer:FT-1`:

| journal_id | account_id                        | direction | amount  |
|------------|-----------------------------------|-----------|---------|
| J-5        | ACC-1300 (Payout Provider Float)  | DEBIT     | 503.000 |
| J-5        | ACC-1150 (Payin Provider Balance) | CREDIT    | 503.000 |

Saldo: `ACC-1150 (Payin Provider Balance) = 0`, `ACC-1300 (Payout Provider Float) = 503.000`.

### 5.5 Payout sukses — `payment.payouts`

| id (payout)                  | withdrawal_id             | provider_id | status    | amount (masuk rek. creator) | provider_fee_amount | provider_reference_id               |
|------------------------------|---------------------------|-------------|-----------|-----------------------------|---------------------|-------------------------------------|
| PO-1 (Payout 497.500 ke BCA) | WD-1 (Tarik dana 500.000) | 2 (Flip)  | COMPLETED | 497.500                     | 3.000               | XND-DISB-8001 (disbursement Flip) |

> Float Flip berkurang `amount + provider_fee = 497.500 + 3.000 = 500.500` (sesuai
> `CREDIT ACC-1300 (Payout Provider Float)` di J-6).

`withdrawals`: WD-1 (Tarik dana 500.000) → `status = PAID`, `completed_at`.

Journal J-6 — `payout:PO-1:completed`:

| journal_id | account_id                        | direction | amount  |
|------------|-----------------------------------|-----------|---------|
| J-6        | ACC-2200 (Withdrawal Payable)     | DEBIT     | 500.000 |
| J-6        | ACC-5100 (Payout Fee Expense)     | DEBIT     | 3.000   |
| J-6        | ACC-4100 (Withdrawal Fee Revenue) | CREDIT    | 2.500   |
| J-6        | ACC-1300 (Payout Provider Float)  | CREDIT    | 500.500 |

Cek: DEBIT 503.000 = CREDIT (2.500 + 500.500) = 503.000 ✓

### 5.6 Saldo akhir withdraw

| account                                | balance | keterangan                            |
|----------------------------------------|---------|---------------------------------------|
| ACC-2110 (Creator Payable — Available) | 0       | creator trima 497.500 di BCA          |
| ACC-2200 (Withdrawal Payable)          | 0       | kewajiban withdrawal lunas            |
| ACC-1150 (Payin Provider Balance)      | 0       | saldo Midtrans terpakai untuk funding |
| ACC-1300 (Payout Provider Float)       | 2.500   | sisa float di Flip                  |
| ACC-4100 (Withdrawal Fee Revenue)      | 2.500   | revenue fee withdraw                  |
| ACC-5100 (Payout Fee Expense)          | 3.000   | expense fee payout                    |

---

## 6. Withdraw gagal

Withdraw `WD-2 (Tarik dana 200.000)`: requested `200.000`, fee `2.500`, net `197.500`. Payout ditolak provider (rekening
invalid).

**State awal** (fokus akun terkait):

| account                                | balance |
|----------------------------------------|---------|
| ACC-1150 (Payin Provider Balance)      | 203.000 |
| ACC-2110 (Creator Payable — Available) | 200.000 |
| ACC-1300 (Payout Provider Float)       | 2.500   |

### 6.1 Journal J-7 — `withdrawal:WD-2:hold`

| journal_id | account_id                             | direction | amount  |
|------------|----------------------------------------|-----------|---------|
| J-7        | ACC-2110 (Creator Payable — Available) | DEBIT     | 200.000 |
| J-7        | ACC-2200 (Withdrawal Payable)          | CREDIT    | 200.000 |

Saldo: `ACC-2110 (Creator Payable — Available) = 0`, `ACC-2200 (Withdrawal Payable) = 200.000`.

### 6.2 Funding FT-2 sebesar 203.000 — Journal J-8

| journal_id | account_id                        | direction | amount  |
|------------|-----------------------------------|-----------|---------|
| J-8        | ACC-1300 (Payout Provider Float)  | DEBIT     | 203.000 |
| J-8        | ACC-1150 (Payin Provider Balance) | CREDIT    | 203.000 |

Saldo: `ACC-1150 (Payin Provider Balance) = 0`, `ACC-1300 (Payout Provider Float) = 205.500`.

### 6.3 Payout gagal — `payment.payouts`

| id (payout)                 | withdrawal_id             | provider_id | status | amount (gagal kirim) | failure_code    | failure_reason             |
|-----------------------------|---------------------------|-------------|--------|----------------------|-----------------|----------------------------|
| PO-2 (Payout gagal 197.500) | WD-2 (Tarik dana 200.000) | 2 (Flip)  | FAILED | 197.500              | ACCOUNT_INVALID | Nomor rekening tidak valid |

`withdrawals`: WD-2 (Tarik dana 200.000) → `status = FAILED`.

Journal J-9 — `payout:PO-2:failed` (lepas hold; **float tidak bergerak**, dana tetap di
`ACC-1300 (Payout Provider Float)`):

| journal_id | account_id                             | direction | amount  |
|------------|----------------------------------------|-----------|---------|
| J-9        | ACC-2200 (Withdrawal Payable)          | DEBIT     | 200.000 |
| J-9        | ACC-2110 (Creator Payable — Available) | CREDIT    | 200.000 |

> Opsional: kalau provider menagih failed fee, tambahkan
> `DEBIT ACC-5100 (Payout Fee Expense) / CREDIT ACC-1300 (Payout Provider Float)`.

### 6.4 Saldo akhir withdraw gagal

| account                                | balance | keterangan                      |
|----------------------------------------|---------|---------------------------------|
| ACC-2110 (Creator Payable — Available) | 200.000 | hak creator kembali, bisa retry |
| ACC-2200 (Withdrawal Payable)          | 0       |                                 |
| ACC-1150 (Payin Provider Balance)      | 0       |                                 |
| ACC-1300 (Payout Provider Float)       | 205.500 | dana tetap siap untuk retry     |
| ACC-5100 (Payout Fee Expense)          | 3.000   | (dari contoh §5)                |

---

## 7. Reversal & Refund

### 7.1 Refund SEBELUM settlement (reversal penuh)

PAY-1 (Donasi 100.000) belum settle, donatur minta refund penuh `100.000` (mis. lewat permintaan langsung). PG
membatalkan, jadi tidak akan pernah settle.

- `payment.refunds`: `RF-1 (Refund penuh PAY-1)`, payment PAY-1 (Donasi 100.000), amount 100.000, status `SUCCEEDED`.
- `payments`: PAY-1 (Donasi 100.000) → `status = REFUNDED`.
- `settlements`: SET-1 (Settlement donasi PAY-1) → `status = CANCELLED`.

Journal J-10 — `refund:RF-1:succeeded` (kebalikan J-1):

| journal_id | account_id                           | direction | amount  |
|------------|--------------------------------------|-----------|---------|
| J-10       | ACC-2100 (Creator Payable — Pending) | DEBIT     | 93.340  |
| J-10       | ACC-4000 (Platform Fee Revenue)      | DEBIT     | 6.000   |
| J-10       | ACC-2300 (VAT Payable)               | DEBIT     | 660     |
| J-10       | ACC-1100 (PG Clearing Receivable)    | CREDIT    | 100.000 |

Efek: `ACC-1100 (PG Clearing Receivable)` kembali 0, `ACC-2100 (Creator Payable — Pending)` kembali 0, revenue & PPN
batal. Tidak ada uang creator yang perlu ditarik karena belum `available`.

### 7.2 Refund SETELAH settlement (uang sudah cair)

PAY-1 (Donasi 100.000) sudah settle; uang ada di `ACC-1150 (Payin Provider Balance)`. Creator **belum** withdraw,
`ACC-2110 (Creator Payable — Available) = 93.340`.

Journal J-11 — `refund:RF-2:succeeded`:

| journal_id | account_id                             | direction | amount  |
|------------|----------------------------------------|-----------|---------|
| J-11       | ACC-2110 (Creator Payable — Available) | DEBIT     | 93.340  |
| J-11       | ACC-4000 (Platform Fee Revenue)        | DEBIT     | 6.000   |
| J-11       | ACC-2300 (VAT Payable)                 | DEBIT     | 660     |
| J-11       | ACC-1150 (Payin Provider Balance)      | CREDIT    | 100.000 |

Efek: `ACC-2110 (Creator Payable — Available)` turun 93.340 (jadi 0), `ACC-1150 (Payin Provider Balance)` turun 100.000.

### 7.3 Refund setelah settlement TAPI creator sudah withdraw

Karena `ACC-2110 (Creator Payable — Available) = 0`, tidak boleh dipaksa negatif. Gunakan akun klaim
`ACC-5300 (Creator Negative Balance)`.

Journal J-12 — `refund:RF-3:succeeded`:

| journal_id | account_id                          | direction | amount  |
|------------|-------------------------------------|-----------|---------|
| J-12       | ACC-5300 (Creator Negative Balance) | DEBIT     | 93.340  |
| J-12       | ACC-4000 (Platform Fee Revenue)     | DEBIT     | 6.000   |
| J-12       | ACC-2300 (VAT Payable)              | DEBIT     | 660     |
| J-12       | ACC-1150 (Payin Provider Balance)   | CREDIT    | 100.000 |

Efek: creator punya "utang" 93.340 (`ACC-5300 (Creator Negative Balance)`), pendapatan creator berikutnya dipakai
melunasi; withdraw diblokir sampai lunas.

### 7.4 Refund sebagian (partial)

Refund `40.000` dari donasi `100.000` (40%):

- creator = 40% × 93.340 = 37.336
- revenue = 40% × 6.000 = 2.400
- VAT = 40% × 660 = 264

Journal J-13 — `refund:RF-4:succeeded`:

| journal_id | account_id                             | direction | amount |
|------------|----------------------------------------|-----------|--------|
| J-13       | ACC-2110 (Creator Payable — Available) | DEBIT     | 37.336 |
| J-13       | ACC-4000 (Platform Fee Revenue)        | DEBIT     | 2.400  |
| J-13       | ACC-2300 (VAT Payable)                 | DEBIT     | 264    |
| J-13       | ACC-1150 (Payin Provider Balance)      | CREDIT    | 40.000 |

`payments.status = PARTIALLY_REFUNDED`. `payment.refunds` bisa punya banyak baris selama
`SUM(amount) ≤ total_charged_amount`.

### 7.5 Chargeback (inisiatif bank/penerbit)

Mirip §7.3, tapi dana ditarik paksa oleh bank dan sering ada chargeback fee:

| journal_id | account_id                          | direction | amount                  |
|------------|-------------------------------------|-----------|-------------------------|
| J-14       | ACC-5300 (Creator Negative Balance) | DEBIT     | 93.340                  |
| J-14       | ACC-4000 (Platform Fee Revenue)     | DEBIT     | 6.000                   |
| J-14       | ACC-2300 (VAT Payable)              | DEBIT     | 660                     |
| J-14       | ACC-5200 (Refund/Chargeback Loss)   | DEBIT     | 15.000 (chargeback fee) |
| J-14       | ACC-1150 (Payin Provider Balance)   | CREDIT    | 115.000                 |

---

## 8. Top-up Flip dari Midtrans (funding lintas provider + adjustment)

### 8.0 Konteks & state awal

Midtrans sudah settle ke **bank** (`settlement_target = BANK`):
`DEBIT ACC-1200 (Bank Operating) / CREDIT ACC-1100 (PG Clearing Receivable)`.

| account                             | balance   | keterangan                     |
|-------------------------------------|-----------|--------------------------------|
| ACC-1200 (Bank Operating)           | 1.000.000 | uang di rekening bank platform |
| ACC-1300 (Payout Provider Float)    | 0         | float Flip masih kosong      |
| ACC-1400 (Fund Transfer In Transit) | 0         | tidak ada transfer in-transit  |

Tujuan: top-up **503.000** ke Flip. Bank adalah rel penghubung (Midtrans → bank → Flip).

### 8.1 Skenario normal (biaya bank jelas)

Bank mengirim 503.000; bank memotong biaya transfer 2.500; Flip menerima 500.500.

`payment.fund_transfers`

| id (fund transfer) | direction | source_type | target_type | counterparty_provider_id | sent_amount | fee_amount |
received_amount | variance_amount | status | bank_reference | provider_reference |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| FT-3 (Top-up Flip 503.000) | TO_PAYOUT_PROVIDER | BANK | PAYOUT_PROVIDER | 2 (Flip) | 503.000 | 2.500 | 500.500 |
0 | COMPLETED | TRF-BANK-9003 (mutasi bank) | XND-TOPUP-7003 (topup Flip) |

Journal J-15 — `fund_transfer:FT-3:out` (Leg 1, uang keluar bank):

| journal_id | account_id                          | direction | amount  |
|------------|-------------------------------------|-----------|---------|
| J-15       | ACC-1400 (Fund Transfer In Transit) | DEBIT     | 503.000 |
| J-15       | ACC-1200 (Bank Operating)           | CREDIT    | 503.000 |

Journal J-16 — `fund_transfer:FT-3:in` (Leg 2, uang masuk Flip + beban fee):

| journal_id | account_id                                  | direction | amount  |
|------------|---------------------------------------------|-----------|---------|
| J-16       | ACC-1300 (Payout Provider Float)            | DEBIT     | 500.500 |
| J-16       | ACC-5150 (Bank / Fund Transfer Fee Expense) | DEBIT     | 2.500   |
| J-16       | ACC-1400 (Fund Transfer In Transit)         | CREDIT    | 503.000 |

Saldo akhir: `ACC-1200 (Bank Operating) = 497.000`, `ACC-1400 (Fund Transfer In Transit) = 0`,
`ACC-1300 (Payout Provider Float) = 500.500`, `ACC-5150 (Bank / Fund Transfer Fee Expense) = 2.500`.
`variance_amount = 503.000 − 2.500 − 500.500 = 0` ✓

### 8.2 Skenario in-transit (Leg 1 sudah, Leg 2 belum)

Mutasi bank sudah ada, Flip belum kredit. Baru J-15 yang diposting.

| account                             | balance                           |
|-------------------------------------|-----------------------------------|
| ACC-1200 (Bank Operating)           | 497.000                           |
| ACC-1400 (Fund Transfer In Transit) | **503.000** (uang "sedang jalan") |
| ACC-1300 (Payout Provider Float)    | 0                                 |

`fund_transfers.status = IN_TRANSIT`. Rekonsiliasi: `saldo ACC-1400 (Fund Transfer In Transit) > 0` + belum ada
`provider_reference` → item `MISSING_EXTERNAL`, job menunggu. Begitu Flip kredit, J-16 diposting dan
`ACC-1400 (Fund Transfer In Transit)` kembali 0. **Uang tidak pernah hilang dari ledger.**

### 8.3 Skenario selisih tidak terjelaskan (adjustment)

Bank kirim 503.000, Flip hanya kredit 500.000, tidak ada bukti fee. `variance_amount = 3.000`.

`payment.fund_transfers`

| id (fund transfer)           | sent_amount | fee_amount | received_amount | variance_amount | status    |
|------------------------------|-------------|------------|-----------------|-----------------|-----------|
| FT-4 (Top-up Flip 503.000) | 503.000     | 0          | 500.000         | 3.000           | COMPLETED |

Journal J-17 (Leg 1): `DEBIT ACC-1400 (Fund Transfer In Transit) 503.000 / CREDIT ACC-1200 (Bank Operating) 503.000`
Journal J-18 (Leg 2):
`DEBIT ACC-1300 (Payout Provider Float) 500.000 / CREDIT ACC-1400 (Fund Transfer In Transit) 500.000`

Setelah itu `ACC-1400 (Fund Transfer In Transit) = 3.000` (sisa tak terjelaskan). Buat `payment.adjustments`:

| id (adjustment)              | scope         | reference_type | reference_id                 | amount | journal_id              | reason                                | requested_by           | approved_by            | status |
|------------------------------|---------------|----------------|------------------------------|--------|-------------------------|---------------------------------------|------------------------|------------------------|--------|
| ADJ-1 (Koreksi selisih FT-4) | FUND_TRANSFER | FUND_TRANSFER  | FT-4 (Top-up Flip 503.000) | 3.000  | J-19 (adjustment:ADJ-1) | Selisih top-up Flip tanpa bukti fee | USER-ADMIN (Admin Ops) | USER-FINANCE (Finance) | POSTED |

Journal J-19 — `adjustment:ADJ-1`:

| journal_id | account_id                          | direction | amount |
|------------|-------------------------------------|-----------|--------|
| J-19       | ACC-5900 (Fund Transfer Variance)   | DEBIT     | 3.000  |
| J-19       | ACC-1400 (Fund Transfer In Transit) | CREDIT    | 3.000  |

`ACC-1400 (Fund Transfer In Transit)` kembali 0; selisih diakui sebagai beban `ACC-5900 (Fund Transfer Variance)`. Tidak
ada jurnal lama yang diubah.

### 8.4 Skenario transfer gagal

Bank menolak transfer; dana kembali ke rekening. Balik Leg 1:

| journal_id | account_id                          | direction | amount  |
|------------|-------------------------------------|-----------|---------|
| J-20       | ACC-1200 (Bank Operating)           | DEBIT     | 503.000 |
| J-20       | ACC-1400 (Fund Transfer In Transit) | CREDIT    | 503.000 |

`fund_transfers.status = FAILED`. Kalau bank menagih failed fee →
`DEBIT ACC-5150 (Bank / Fund Transfer Fee Expense) / CREDIT ACC-1200 (Bank Operating)`.

### 8.5 Kalau sumber dana dari SALDO PG (bukan bank)

Jika provider payin settle ke saldo sendiri (`settlement_target = PROVIDER_BALANCE`,
`ACC-1150 (Payin Provider Balance)`), untuk top-up ke provider lain tetap ditarik ke bank dulu:

```
Leg A (keluar dari saldo PG):  DEBIT ACC-1400 (Fund Transfer In Transit) / CREDIT ACC-1150 (Payin Provider Balance)   -- J-21
Leg B (masuk ke bank):         DEBIT ACC-1200 (Bank Operating) / CREDIT ACC-1400 (Fund Transfer In Transit)   -- J-22
Lalu bank -> Flip seperti §8.1 (J-15, J-16)
```

Kesimpulan: lintas provider = **minimal 2 leg, umumnya 4 leg** (saldo PG → bank → provider tujuan). Semua leg dicatat,
`ACC-1400 (Fund Transfer In Transit)` menjembatani selisih waktu, dan selisih tak terjelaskan selalu lewat
`payment.adjustments`.

---

## 9. Aturan yang dibuktikan contoh ini

1. **Tidak ada journal tanpa sumber** — tiap journal punya `reference_type` + `reference_id` + `idempotency_key`.
2. **DEBIT = CREDIT** di setiap journal.
3. **Timer tidak pernah mem-posting settlement** — J-2 (settlement:SET-1:confirmed) hanya muncul setelah
   `SET-1 (Settlement donasi PAY-1) = CONFIRMED` dengan `raw_evidence`.
4. **Available hanya setelah settlement** — §4.
5. **Withdraw lewat hold** — tidak mungkin double-withdraw karena `idempotency_key`.
6. **Payout gagal mengembalikan hak creator** — §6, float tidak hilang.
7. **Refund selalu journal baru** (`reverses_journal_id` saat reversal); tidak pernah meng-`UPDATE`/`DELETE` entry lama.
8. **Saldo akun selalu bisa direkonsiliasi**: `balance` = Σ (kredit) − Σ (debit) sesuai arah normal.
9. **Funding lintas provider dicatat 2 leg** via `ACC-1400 (Fund Transfer In Transit)` in-transit — uang tidak pernah
   "hilang" walau transfer belum selesai.
10. **Selisih transfer tak terjelaskan lewat `payment.adjustments`** (reason + approver) → jurnal `ADJUSTMENT`, bukan
    mengubah jurnal lama.
