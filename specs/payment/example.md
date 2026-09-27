# Payment Module — Contoh Kasus Konkret

Asumsi config:
- Channel `BCA_VA` via Xendit — `pg_fixed_fee=4.500`, `pg_percentage_fee_bps=0`, `vat_bps=1100` (11%)
- Platform fee rule aktif — `fixed=0`, `percentage_bps=500` (5%)
- Streamer = "Rani"

**Prinsip fee (koreksi penting):** fee PG itu *pass-through*, platform tidak menanggung maupun mengambil untung darinya.
- Donasi → fee PG ditanggung **donatur**, ditambahkan di atas nominal donasi.
- Withdrawal → fee PG ditanggung **streamer**, ditambahkan di atas nominal yang diminta.
- Platform **hanya** ambil `platform_fee` dari nominal donasi (5% global, bisa di-override per user), tidak pernah dari fee PG.

**Catatan:** semua contoh di bawah pakai 1 PG (Xendit) saja untuk simplisitas, jadi wallet `PG_CLEARING` di tabel = `PG_CLEARING (Xendit)`. Kalau donasi lain lewat Midtrans, itu wallet `PG_CLEARING` yang BEDA (per payment_gateway), tidak dicampur di baris yang sama.

---

## 1. Donation Payment — donasi Rp100.000

**Kondisi awal wallet** (semua 0)

| wallet | balance | pending_balance |
|---|---|---|
| STREAMER (Rani) | 0 | 0 |
| PLATFORM_FEE | 0 | 0 |
| PG_CLEARING | 0 | 0 |

**`transactions`**

| field | value | keterangan |
|---|---|---|
| type | `DONATION_PAYMENT` | |
| gross_amount | 100.000 | basis donasi, bukan yg dibayar donatur |
| pg_fixed_fee_amount | 4.500 | |
| pg_vat_amount | 495 | 11% × 4.500 |
| **pg_fee_amount** | **4.995** | informational — ditanggung donatur, TIDAK masuk ledger platform |
| **total_charged_amount** | **104.995** | gross_amount + pg_fee_amount = yg BENAR2 dibayar donatur |
| **platform_fee_amount** | **5.000** | 5% × gross_amount |
| **net_amount** | **95.000** | gross_amount − platform_fee_amount → masuk wallet Rani |

**`journals`**: `"Donation payment received"`

**`ledger_entries`** — cuma **3 baris** (bukan 4, karena pg_fee tidak menyentuh ledger platform)

| # | wallet | direction | amount | balance_before | balance_after | entry_type |
|---|---|---|---|---|---|---|
| 1 | PG_CLEARING | DEBIT | 100.000 | 0 | 100.000 | GATEWAY_INFLOW |
| 2 | STREAMER | CREDIT | 95.000 | 0 | 95.000 | DONATION_IN |
| 3 | PLATFORM_FEE | CREDIT | 5.000 | 0 | 5.000 | PLATFORM_FEE_REVENUE |

✅ `DEBIT(100.000) = CREDIT(95.000 + 5.000)`. Angka `104.995` yang dibayar donatur **tidak pernah muncul di ledger** — itu terjadi di sisi PG (mereka nagih 104.995, ambil fee 4.995 sendiri, remit 100.000 bersih ke clearing). `pg_fee_amount` cuma dipakai untuk kuitansi donatur + pembukuan PPN masukan Gepay (masih perlu dicatat untuk pajak walau ditanggung donatur).

**Kondisi akhir**

| wallet | balance | pending_balance |
|---|---|---|
| STREAMER | 95.000 | 95.000 |
| PLATFORM_FEE | 5.000 | 5.000 |
| PG_CLEARING | 100.000 | — |

---

## 2. Withdrawal — Rani minta terima Rp10.000 di rekening bank

Channel `BCA_TRANSFER` (`direction='OUTBOUND'`), config: `pg_fixed_fee=2.500`, `vat_bps=1100`, `min_amount=10.000`. Platform fee withdrawal untuk contoh ini **0** (opsional, bisa diaktifkan kapan saja tanpa ubah schema).

**Kondisi awal** (asumsi sudah settled)

| wallet | balance | pending_balance | available |
|---|---|---|---|
| STREAMER | 95.000 | 0 | 95.000 |
| PG_CLEARING | 100.000 | — | — |

**Validasi awal service layer:** `requested_amount (10.000) >= gateway_channel_configs.min_amount (10.000)` ✔ lolos (pas di batas minimum).

**`transactions`**

| field | value | keterangan |
|---|---|---|
| type | `WITHDRAWAL` | |
| gross_amount | 10.000 | nominal yang DIMINTA Rani untuk diterima di bank |
| pg_fixed_fee_amount | 2.500 | |
| pg_vat_amount | 275 | 11% × 2.500 |
| **pg_fee_amount** | **2.775** | ditanggung Rani, ditambahkan DI ATAS gross_amount |
| platform_fee_amount | 0 | contoh ini 0, tapi kolomnya sudah siap dipakai (fixed/percentage) |
| **total_charged_amount** | **12.775** | gross_amount + pg_fee_amount + platform_fee_amount → yg BENAR2 didebit dari wallet |
| **net_amount** | **10.000** | = gross_amount → yg BENAR2 masuk rekening bank Rani |

**Service layer sebelum insert:**
```sql
SELECT * FROM payment.wallets WHERE id = :walletId FOR UPDATE;  -- row lock, wajib
-- cek: available_balance (95.000) >= total_charged_amount (12.775) → lolos
```

**`ledger_entries`** — simetris persis dengan donasi (cuma arah kebalik), 2 baris karena `platform_fee_amount=0` (baris ke-3 di-skip kalau amount=0, constraint `amount > 0`)

| # | wallet | direction | amount | balance_before | balance_after | entry_type |
|---|---|---|---|---|---|---|
| 1 | STREAMER | DEBIT | 12.775 | 95.000 | 82.225 | WITHDRAWAL |
| 2 | PG_CLEARING | CREDIT | 12.775 | 100.000 | 87.225 | WITHDRAWAL |

> Kalau `platform_fee_amount > 0`: tambah baris ke-3 `CREDIT PLATFORM_FEE platform_fee_amount`, dan baris ke-2 `PG_CLEARING` jadi `total_charged_amount − platform_fee_amount` (persis pola donasi, cuma kebalik arah).

**Kondisi akhir**: STREAMER `balance=82.225`. Rani lihat di app: "Minta tarik 10.000, fee 2.775, total terpotong dari saldo 12.775".

### 2b. Kalau disbursement GAGAL

Journal reversal baru (bukan edit), pakai `total_charged_amount` (12.775) yang sama:

| # | wallet | direction | amount | balance_before | balance_after | entry_type |
|---|---|---|---|---|---|---|
| 1 | STREAMER | CREDIT | 12.775 | 82.225 | 95.000 | WITHDRAWAL_REVERSAL |
| 2 | PG_CLEARING | DEBIT | 12.775 | 87.225 | 100.000 | WITHDRAWAL_REVERSAL |

`transactions.status = FAILED`.

---

## 3. Refund — dibahas sebagai keputusan bisnis dulu (lihat bagian bawah), bukan schema

Skip contoh angka dulu — lihat rekomendasi di bagian **"Soal fitur refund"** di bawah, karena ini keputusan produk, bukan cuma soal ledger.

---

## 4. Manual Adjustment — koreksi saldo Rani +10.000

**`manual_adjustments`**

| field | value |
|---|---|
| wallet_id | `<wallet Rani>` |
| direction | `CREDIT` |
| amount | 10.000 |
| reason | `"Koreksi selisih settlement batch 2026-09-20, tiket SUPPORT-1234"` |
| requested_by | admin A |
| approved_by | admin B *(wajib beda — segregation of duty)* |
| status | `APPROVED` |

**`ledger_entries`**

| # | wallet | direction | amount | balance_before | balance_after | entry_type |
|---|---|---|---|---|---|---|
| 1 | STREAMER | CREDIT | 10.000 | 82.225 | 92.225 | ADJUSTMENT |

---

## Soal fitur refund — ini keputusan bisnis, bukan cuma teknis

Pertanyaanmu ada 2 bagian, aku jawab terpisah:

### A. Boleh gak kebijakan "refund ditolak kalau sudah ditarik streamer"?

**Ya, ini kebijakan yang sah dan umum dipakai.** Bukan cuma boleh, malah ini **best practice** — hampir semua platform donation/marketplace membatasi refund ke dana yang masih "in escrow" (belum di-withdraw). Implementasinya simpel: sebelum approve refund, cek:
```sql
IF streamer_wallet.available_balance < refund_net_amount THEN
    REJECT -- atau escalate ke proses "collection"/hutang manual, di luar wallet
```
Kalau ditolak otomatis, refund untuk kasus itu ditangani manual (admin nagih balik ke streamer di luar sistem) — bukan sesuatu yang harus di-handle otomatis oleh ledger.

### B. Apakah lebih baik gak usah ada fitur refund sama sekali?

**Pertimbangan yang perlu kamu tahu:** secara hukum Indonesia, donasi ke kreator itu biasanya dikategorikan sebagai **hibah/pemberian sukarela**, bukan transaksi jual-beli — beda dengan belanja online yang punya hak refund konsumen. Ini kemungkinan **kenapa Saweria/Trakteer sepengetahuanku juga tidak punya fitur "refund" untuk donatur** yang berubah pikiran. Yang biasa ada cuma:
- **Chargeback** dari sisi bank/PG (dispute kartu kredit, bukan inisiatif platform) — ini datang dari luar, platform cuma react.
- **Refund atas kasus fraud/error teknis** (misal salah nominal karena bug) — sifatnya eksepsional, di-handle manual oleh admin.

**Rekomendasiku:** untuk MVP, **jangan bangun fitur "refund" sebagai flow otomatis yang bisa di-trigger user**. Cukup:
- Simpan kolom `refund_original_transaction_id` di schema (murah, sudah ada, gak makan biaya dev sekarang).
- Kalau ada kasus refund yang genuinely perlu (fraud/error/dispute), tangani via **`manual_adjustments`** yang sudah ada (dengan approval 2 admin) — bukan bikin `type='REFUND'` dengan flow otomatis sendiri.
- Ini mengurangi kompleksitas (gak perlu guard `available_balance` khusus refund, gak perlu UI refund) tanpa kehilangan kemampuan audit — kalau butuh refund kasuistik, `manual_adjustments.reason` sudah cukup jelas tercatat.

Kalau nanti bisnis berkembang dan butuh refund self-service (misal donasi via kartu kredit yang legally punya hak refund beda dari VA/QRIS), baru dibangun `type='REFUND'` penuh — schema-nya sudah siap tinggal dipakai.

---

## Ringkasan pola konsisten

1. journal → ledger entries → update status, semua dalam **1 `@Transactional`**.
2. `ledger_entries` tidak pernah di-`UPDATE`/`DELETE` — koreksi = entry baru.
3. Fee PG = pass-through, informational, tidak pernah masuk ledger platform.
4. Fee platform hanya ada di donasi, dari `gross_amount`, tidak pernah dari fee PG.
5. `SUM(debit) == SUM(credit)` per journal — jadikan ini assertion di test.