# Glosarium — Bahasa Manusia

> Semua istilah yang dipakai di project ini, dijelaskan untuk orang yang **bukan
> akuntan**. Baca ini dulu kalau ada kata yang terasa aneh.
> Kembali ke [pintu masuk](./README.md).

---

## Cara pakai

- **[Bagian A](#bagian-a-dasar-akuntansi)** — hanya perlu sekali, untuk paham "buku besar".
- **[Bagian B](#bagian-b-istilah-project)** — istilah bisnis sehari-hari.
- **[Bagian C](#bagian-c-daftar-akun-ledger)** — daftar akun & artinya.

---

## Bagian A — Dasar akuntansi

### Ledger (buku besar)

Bayangkan **buku tabungan untuk setiap pihak**: donatur, creator, Midtrans, Flip,
bank, dan platform itu sendiri. Setiap kali uang bergerak, kita menulis satu baris
di buku yang relevan. Ledger = kumpulan buku-buku itu.

Bedanya dengan saldo biasa: ledger menyimpan **riwayat lengkap**, bukan hanya angka
akhir. Dari riwayat itu, saldo akhir dihitung.

### Debit / Kredit

Ini dua **kolom** di buku besar. **Bukan** artinya "tambah" dan "kurang".

- Menambah aset (mis. uang di bank) = **debit**.
- Menambah utang/hak orang lain (mis. kewajiban ke creator) = **kredit**.

Karena setiap gerakan menyentuh minimal dua buku, satu baris debit selalu
berpasangan dengan satu baris kredit dengan nilai sama. Itu sebabnya disebut
**double-entry** (pencatatan berpasangan).

### Jenis akun

| Jenis | Artinya | Saldo tumbuh saat | Contoh |
|---|---|---|---|
| **Aset** | yang **kita miliki / hak kita** | debit | uang di bank, piutang ke Midtrans |
| **Liabilitas** | yang **kita utang** (hak orang lain) | kredit | hak creator yang belum dibayar |
| **Pendapatan (Revenue)** | penghasilan kita | kredit | fee platform dari donasi |
| **Beban (Expense)** | biaya kita | debit | fee payout provider |
| **Ekuitas** | modal | kredit | (belum dipakai) |

### Double-entry (pencatatan berpasangan)

Aturan mutlak: **total debit harus = total kredit**. Kalau tidak sama, jurnal
ditolak (`ledger.unbalanced_journal`). Ini yang membuat pembukuan selalu
konsisten — uang tidak bisa muncul/hilang diam-diam.

### Jurnal & baris jurnal (journal & entry)

- **Journal** = satu kejadian, mis. "donasi PAY-1 dibayar". Punya
  `idempotency_key` unik supaya tidak tercatat dua kali.
- **Entry** = satu baris debit/kredit di dalam jurnal. Minimal 2 baris per jurnal.

### Saldo normal (normal balance)

Setiap jenis akun "tumbuh" ke satu arah: aset/beban tumbuh saat **debit**;
liabilitas/pendapatan tumbuh saat **kredit**. Itulah "saldo normal".

### Akun global vs akun per-owner

- **Global** (tanpa pemilik) — satu akun untuk semua, mis. `VAT Payable`.
- **Per-owner** — satu akun per creator/provider, mis. hak creator `USER-123`
  berbeda dari `USER-456`. Dibuat otomatis saat pertama kali dipakai (*lazy*).

### Append-only & reversal

Riwayat jurnal **tidak pernah diubah/dihapus**. Kalau ada kesalahan, dibuat jurnal
**pembalikan (reversal)** baru yang menunjuk jurnal aslinya. Jadi audit trail utuh.

### Idempotency key

Penanda unik satu kejadian, mis. `PAYMENT:PAY-1:PAID`. Kalau webhook/request
datang dua kali dengan key sama, yang kedua **diabaikan** (tidak posting ulang).
Ini pelindung utama dari pencatatan ganda.

---

## Bagian B — Istilah project

### Payin / Payout

- **Payin** = uang **masuk** ke platform (donasi dari donatur).
- **Payout** = uang **keluar** dari platform (dikirim ke rekening creator).

### Provider / Channel / Route

- **Provider** = penyedia layanan, mis. `MIDTRANS` (payin), `FLIP` (payout).
- **Channel** = metode/cara bayar yang stabil & netral, mis. `VA_BCA`, `QRIS`,
  `BANK_BCA`. Dipakai FE/API, bukan kode vendor.
- **Route** = "channel X dilayani provider Y dengan kebijakan Z". Tabel
  `payment.channel_routes`.

### Akun-akun penting di ledger

| Akun | Bahasa manusia |
|---|---|
| `PG_CLEARING_RECEIVABLE` | **Piutang ke PG**: donatur sudah bayar, tapi uang belum masuk bank kita |
| `PAYIN_PROVIDER_BALANCE` | Uang kita yang masih "titip" di saldo provider payin (belum ke bank) |
| `BANK_OPERATING` | Rekening bank operasional platform (uang riil kita) |
| `PAYOUT_PROVIDER_FLOAT` | Saldo yang kita titipkan di provider payout (Flip) untuk bayar creator |
| `CREATOR_PAYABLE_PENDING` | Hak creator yang **belum boleh ditarik** |
| `CREATOR_PAYABLE_AVAILABLE` | Hak creator yang **sudah boleh ditarik** |
| `WITHDRAWAL_PAYABLE` | Hak creator yang **sedang ditarik** (ditahan sementara) |
| `VAT_PAYABLE` | PPN yang kita utang ke negara |
| `PLATFORM_FEE_REVENUE` | Pendapatan fee platform |
| `WITHDRAWAL_FEE_REVENUE` | Pendapatan fee penarikan |
| `PG_FEE_EXPENSE` | Biaya fee payment gateway (hanya kalau kita yang menanggung) |
| `PAYOUT_FEE_EXPENSE` | Biaya fee payout provider |
| `FUND_TRANSFER_VARIANCE` | Selisih tak terjelaskan saat transfer antar akun |
| `CREATOR_NEGATIVE_BALANCE` | Creator berutang ke kita (mis. setelah chargeback) |

Daftar lengkap: [Bagian C](#bagian-c-daftar-akun-ledger).

### Fee

- **Platform fee** — fee yang diambil platform (bagian pendapatan kita).
- **PG fee (gateway fee)** — biaya Midtrans. **Diputuskan ditanggung donatur**
  (donatur bayar donasi + fee), jadi tidak mengurangi uang yang kita terima. Lihat
  [manual-settlement §7](./payment/manual-settlement.md).
- **Payout fee** — biaya Flip saat mengirim uang ke creator.
- **PPN / VAT** — pajak (Indonesia 11%), dihitung di atas fee platform.

### Pending / Available / Hold

Tiga kondisi hak creator:

- **Pending** = creator sudah berhak, tapi uang belum benar-benar cair → **belum
  boleh** ditarik.
- **Available** = uang sudah cair ke bank platform → **boleh** ditarik.
- **Hold** (di `Withdrawal Payable`) = creator sedang menarik; uangnya "dikunci"
  supaya tidak bisa dipakai ganda.

### Settlement punya 3 arti

Ini sumber kebingungan terbesar. Tiga hal yang berbeda:

| # | Arti | Sumber | Peran di sistem |
|---|---|---|---|
| **A** | Status di **Midtrans**: transaksi sudah "settled" di sisi PG | webhook/CSV Midtrans | penanda **Paid** (kita: `payments.status=PAID`) |
| **B** | **Withdrawable di PG**: saldo di Midtrans sudah boleh ditarik ke bank | dashboard MAP | hanya untuk **pantauan**, tidak ada kolom khusus |
| **C** | **Settlement kita**: uang sudah benar-benar masuk rekening bank | mutasi bank / bukti | **inilah yang memicu jurnal J-2 & J-3** |

Midtrans pakai kata "settlement" untuk **A**. Jangan disamakan dengan **C**.
Alur admin: [manual-settlement.md](./payment/manual-settlement.md).

### Withdrawable

Saldo di provider yang **sudah boleh ditarik** ke rekening bank. Midtrans
membedakan "Withdrawable balance" vs "Pending withdrawable balance".

### T+n hari kerja

Perkiraan kapan dana PG menjadi bisa ditarik, dihitung **n hari kerja** dari
`paid_at` (Sabtu/Minggu & libur nasional dilewati). Dipakai hanya untuk **monitor**
(menandai yang telat), **bukan** pemicu jurnal.

### Evidence (bukti)

Settlement tidak boleh dipicu tebakan. Wajib ada bukti:
`API`, `REPORT_FILE` (CSV), `BANK_STATEMENT` (mutasi bank), atau `MANUAL`.

### Batch settlement

Satu **header** `payment.settlements` yang mewakili satu pencairan dari satu
provider. Berisi `expected_amount` (Σ transaksi yang di-match), `actual_amount`
(nominal bukti), dan `variance_amount` (selisihnya). 1 payment masuk 1 batch.

### Variance

Selisih `actual_amount − expected_amount`. Idealnya 0. Kalau bukan 0, harus
diselidiki (jangan asal dibukukan ke `FUND_TRANSFER_VARIANCE`).

### Vendor-blind

Modul tidak mengimpor tipe Midtrans/Flip. Bahasa vendor diterjemahkan oleh
**adapter** per provider, sehingga menambah PG = tambah adapter, tanpa ubah
service/entity.

### Adapter

Kode yang menerjemahkan bahasa vendor ↔ bahasa internal kita. Mis.
`MidtransPayinClient`, `FlipPayoutClient`.

### Inbox / `processed_events`

Tabel untuk memastikan webhook yang sama datang dua kali tetap diproses sekali
(unique per provider + event id).

### Ledger tidak di-cache

Saldo adalah data krusial; membaca saldo **selalu** dari database agar tidak
menyajikan angka basi. Ini pengecualian sadar dari kebijakan cache umum.

---

## Bagian C — Daftar akun ledger

| Kode | Nama | Jenis | Saldo normal | Pemilik |
|---|---|---|---|---|
| 1100 | PG Clearing Receivable | Aset | Debit | provider payin |
| 1150 | Payin Provider Balance | Aset | Debit | provider payin |
| 1200 | Bank Operating | Aset | Debit | bank (`BANK-1`) |
| 1300 | Payout Provider Float | Aset | Debit | provider payout |
| 1400 | Fund Transfer In Transit | Aset | Debit | global |
| 2100 | Creator Payable - Pending | Liabilitas | Kredit | user |
| 2110 | Creator Payable - Available | Liabilitas | Kredit | user |
| 2200 | Withdrawal Payable | Liabilitas | Kredit | user |
| 2300 | VAT Payable | Liabilitas | Kredit | global |
| 4000 | Platform Fee Revenue | Pendapatan | Kredit | global |
| 4100 | Withdrawal Fee Revenue | Pendapatan | Kredit | global |
| 5000 | Payment Gateway Fee Expense | Beban | Debit | global |
| 5100 | Payout Fee Expense | Beban | Debit | global |
| 5150 | Bank / Fund Transfer Fee Expense | Beban | Debit | global |
| 5200 | Refund / Chargeback Loss | Beban | Debit | global |
| 5300 | Creator Negative Balance | Aset | Debit | user |
| 5900 | Fund Transfer Variance | Beban | Debit | global |

Sumber kebenaran: enum `AccountCode` (`ledger/api/enums/AccountCode.java`).
