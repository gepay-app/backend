# GePay — Panduan Dokumentasi

> **Mulai dari sini.** Dokumen ini menjelaskan sistem dengan bahasa manusia, bukan
> bahasa akuntansi. Untuk aturan teknis (normatif), lihat [`../AGENTS.md`](../AGENTS.md).

---

## 1. GePay dalam satu menit

GePay adalah backend untuk **donasi/pembelian konten**. Pengguna membayar ke platform,
platform memotong fee-nya, lalu sisanya menjadi **hak creator** yang bisa ditarik
(withdraw) ke rekening creator.

Uang tidak pindah langsung; ia melewati beberapa tempat:

```
Pengguna → Payment Gateway (Midtrans) → Rekening bank platform → Payout provider (Flip) → Creator
```

Di setiap perpindahan, sistem mencatat "siapa berutang/berhak berapa". Catatan itulah
**ledger (buku besar)**.

Satu kata yang paling sering bikin bingung: **"settlement"** punya 3 arti berbeda.
Penjelasannya di [Glosarium](./glossary.md#settlement-punya-3-arti).

---

## 2. Peta modul

| Modul | Tugas | Isi |
|---|---|---|
| `platform` | fondasi bersama | format response, error, i18n, Redis, scheduler |
| `identity` | siapa pemakainya | login (Firebase), user, role |
| `ledger` | **buku besar** | akun, jurnal, saldo creator |
| `payment` | **mesin pembayaran** | payin, settlement, tarik dana, payout (engine generik) |
| `donation` | **consumer payment** | halaman donasi, donasi TEXT/YOUTUBE, overlay OBS (queue + WebSocket) |

Aturan besar: **`payment` memutuskan apa yang bergerak, `ledger` yang mencatat angkanya.**
`payment` memanggil `ledger` dan tidak pernah menulis jurnal sendiri; `ledger` tidak tahu
soal Midtrans/Flip.

> `payment` adalah **engine generik**: ia tidak tahu aturan donasi/konten. Modul
> `donation` adalah **consumer** yang memanggil `payment` lewat `payment::api`
> (`type=DONATION`). Lihat [Donation](./donation.md).

---

## 3. Alur uang singkat

```
1. Pembayaran masuk         → payments PAID, hak creator masih "pending"   → J-1
2. T+n hari kerja lewat     → job Quartz settlement otomatis              → J-2, J-3
3. Platform top-up Flip     → pindah uang sendiri (bukan biaya)           → J-4
4. Creator minta tarik      → uang creator dikunci (hold)                 → J-5
5. Flip kirim ke creator    → payout selesai                              → J-6
```

---

## 4. Urutan baca

**Ledger (buku besar) — `readme/ledger/`**
1. [Konsep & API](./ledger/overview.md) — aturan double-entry, `LedgerApi`, error.
2. [Daftar akun](./ledger/chart-of-accounts.md) — semua akun + penjelasan.
3. [Contoh jurnal](./ledger/examples.md) — J-1..J-11.
4. [Implementasi](./ledger/implementation.md) — isi kode (as-built).

**Payment (alur uang) — `readme/payment/`**
5. [Konsep & alur](./payment/overview.md) — prinsip, 3 arti settlement, koneksi ke ledger.
6. [Status](./payment/states.md) — semua state machine.
7. [Settlement otomatis](./payment/settlement.md) — job Quartz.
8. [Contoh lengkap](./payment/example.md) — angka per langkah.

**Donation (consumer) — `readme/`**
9. [Donation](./donation.md) — halaman, donasi, overlay OBS (queue + WebSocket + SSE).

**Istilah:** [Glosarium](./glossary.md). Rencana kerja: [`../todo.md`](../todo.md).

---

## 5. Status implementasi

| Modul | Status | Keterangan |
|---|---|---|
| `platform` | ✅ stabil | response envelope, error, i18n, Redis, Quartz |
| `identity` | ✅ stabil | Firebase auth, user, role |
| `ledger` | ✅ stabil | double-entry, lazy account, saldo, jurnal |
| `payment` | 🟡 berjalan | payin + webhook (Redis stream), settlement (T+n), withdraw/payout; M5 (refund/reconciliation) ditunda |
| `donation` | 🟡 berjalan | halaman + donasi TEXT/YOUTUBE, overlay queue + WebSocket + SSE |

Rencana kerja berurut: [`../todo.md`](../todo.md).

---

## 6. Konvensi singkat

Semua aturan main ada di [`../AGENTS.md`](../AGENTS.md). Tiga yang paling sering muncul:

- Response HTTP selalu dibungkus `{message, data}` / `{code, message, errors}`.
- `@Transactional` hanya di service, bukan controller/repository.
- `payment` hanya boleh memakai API publik `ledger` (`LedgerApi`).
