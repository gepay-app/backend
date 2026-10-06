# Manual Settlement — Panduan Operasional (Admin)

> Pendamping [`payment-design.md`](./payment-design.md) §5. Menjelaskan **apa yang
> admin lakukan** untuk memindahkan dana Midtrans → rekening bank, **kenapa CSV
> harian tidak cukup**, dan **jurnal apa yang diposting**.
>
> Bingung istilah? [Glosarium](../glossary.md), terutama
> ["Settlement punya 3 arti"](../glossary.md#settlement-punya-3-arti). Daftar status:
> [states.md](./states.md).
>
> Contoh CSV: [`balance-transaction-report-G548772047-06-10-2026.csv`](./balance-transaction-report-G548772047-06-10-2026.csv).

---

## 0. TL;DR

- Di Midtrans, `Status = settlement` hanya berarti **uang sudah masuk saldo
  Midtrans**; kita mencatatnya sebagai **`PAID`**. Itu **bukan** "uang sudah di
  rekening bank" dan **bukan** "sudah bisa ditarik". Karena itu **jangan** memakai
  kata "settled" untuk status PG ini.
- Di aplikasi, **settled** punya tepat satu arti: transaksi sudah masuk batch
  settlement yang `CONFIRMED`, sehingga hak creator `PENDING → AVAILABLE` dan
  **bisa ditarik**.
- Penentu "benar-benar cair" = **mutasi bank** (dan/atau instruksi pencairan dari
  MAP). Batch settlement **hanya dikonfirmasi** kalau bukti dana masuk sudah ada.
- Pencairan dari PG adalah **aksi admin**: uang riil keluar dari saldo PG ke
  rekening bank. Karena itu wajar dan memang **harus** dijalankan admin/ops.
- Tapi **memilih transaksi tidak manual**: sistem memilihnya otomatis by rule
  (§5.3), lalu admin memverifikasi **satu angka total** terhadap mutasi bank.
- Beban admin sebanding dengan **jumlah pencairan**, bukan jumlah transaksi: satu
  pencairan = satu batch, walau transaksinya ratusan ribu (§5.8).
- Konfirmasi batch → sistem posting **J-2** (dana ke bank) + **J-3** (creator
  `PENDING → AVAILABLE`).

---

## 1. Tiga konsep yang sering dicampur

| # | Konsep | Arti | Sumber sinyal | Disimpan di |
|---|--------|------|---------------|-------------|
| A | **PAID** (Midtrans `settlement`) | uang diterima PG, PG berutang ke kita | webhook `settlement` **atau** CSV harian | `payments.status=PAID`, `paid_at` |
| B | **Withdrawable di PG** | saldo PG sudah boleh ditarik ke bank | dashboard MAP ("Withdrawable balance") | tidak ada kolom (lihat §3) |
| C | **Cash in bank** | uang benar-benar ada di rekening bank | mutasi bank | `settlements.actual_amount`, `actual_settled_at` |

**Aturan**: batch settlement (dan jurnal J-2/J-3) **hanya** dipicu saat konsep **C**
(bukti dana masuk). A dan B dipakai untuk memantau dan memilih transaksi.

> Midtrans memakai kata `settlement` untuk konsep **A**. Jangan disamakan dengan
> "settled"-nya kita (= **C**). Di dokumen ini status Midtrans selalu ditulis dalam
> backtick (`settlement`) supaya jelas itu istilah vendor.

---

## 2. Sumber data & kegunaannya

| Sumber | Memberi tahu | TIDAK memberi tahu |
|--------|--------------|--------------------|
| CSV harian (Balance transaction report) | `Order ID`, `Amount`, `Total Fee`, `Status=settlement`, `Settlement time` | withdrawable/pending, nominal yang benar-benar masuk bank |
| Dashboard MAP / disbursement report | withdrawable vs pending withdrawable, nominal pencairan | daftar order detail (kecuali disediakan) |
| Mutasi bank | nominal cash akurat + tanggal | order mana yang tercakup (harus di-match) |

Kesimpulan: **daftar transaksi yang tercakup** (order mana) dan **nominal cash**
(berapa) datang dari dua sumber berbeda. CSV/report menjawab "order mana yang sudah
`PAID` di PG" — **bukan** "mana yang sudah cair di bank". Itulah alasan batch
`settlements` punya `expected_amount` (Σ transaksi yang dipilih) **dan**
`actual_amount` (bukti), plus `variance_amount`.

---

## 3. Kenapa CSV tidak bisa menjawab "sudah bisa di-withdraw?"

Dari CSV contoh (ringkas):

| Date Created (WIB) | Order ID | Amount | Total Fee | Status | Settlement time |
|---|---|---|---|---|---|
| 2026-10-06 14:01 | `order3` | 20.000 | -4.440 | settlement | 2026-10-06 14:01 |
| 2026-10-05 20:55 | `01a10c58-…-88f6927869bd` | 20.000 | -4.440 | settlement | 2026-10-05 20:56 |
| 2026-10-05 18:51 | `order2` | 20.000 | -4.440 | settlement | 2026-10-05 18:51 |
| 2026-10-05 18:45 | `order1` | 20.000 | -4.440 | settlement | 2026-10-05 18:45 |
| 2026-10-05 18:41 | `order14` | 20.000 | -4.440 | settlement | 2026-10-05 18:43 |

Semua baris berstatus `settlement` (artinya: sudah **PAID** di PG), **tidak ada**
bedanya di CSV. Padahal menurut MAP:

- 4 transaksi **5 Okt** → sudah **Withdrawable balance**.
- 1 transaksi **6 Okt** (`order3`) → masih **Pending withdrawable**.

> Angka withdrawable di CSV/uji ini net (`20.000 − 4.440 = 15.560`) karena charge
> test-nya **lupa menambahkan fee PG** ke `gross_amount` (lihat §7). Untuk
> mempelajari *status* withdrawable, lihat polanya: 4 trx 5 Okt cair, 1 trx 6 Okt
> belum — bukan angkanya.

Jadi status withdrawable **tidak bisa diturunkan dari CSV**. Yang membedakan adalah
waktu: transaksi 5 Okt sudah lewat cut-off pencairan, yang 6 Okt belum. Itu pula
sebabnya `channel_routes.settlement_delay_days` (T+n) tetap ada: ia **memperkirakan**
kapan transaksi menjadi withdrawable — dipakai untuk monitor OVERDUE dan untuk rule
auto-match (§5.3) — **bukan** pemicu jurnal.

> **Catatan konfigurasi**: contoh di atas berarti transaksi 5 Okt withdrawable pada
> 6 Okt, sedangkan 6 Okt belum → aturan withdrawable MAP di akun ini ≈ **T+1 hari
> kerja**. Seed kita sekarang `settlement_delay_days = 3`. Sesuaikan dengan aturan
> MAP akun kalian supaya `expected_settlement_date` (monitor OVERDUE) bermakna.

---

## 4. Data model yang dipakai

```
payment.settlements            (1 header per pencairan/provider)
  provider_id, status PENDING|CONFIRMED|CANCELLED,
  expected_amount, actual_amount, variance_amount,
  settlement_target BANK|PROVIDER_BALANCE,
  period_start, period_end, actual_settled_at,
  evidence_source (API|REPORT_FILE|BANK_STATEMENT|MANUAL),
  evidence_reference, raw_evidence, created_by, confirmed_at

payment.payments.settlement_id (FK) -> batch yang memuat payment ini
payment.payments.settled_at
payment.payment_attempts.provider_reference_id  == kolom "Order ID" Midtrans
```

Hubungan: 1 `payment` = 1 batch (`payments.settlement_id`), tanpa tabel item.

---

## 5. Alur admin (step-by-step)

### 5.1 Prasyarat
- Webhook `PAYMENT_PAID` sudah jalan → `payments.status = PAID`, `paid_at`,
  `expected_settlement_date` terisi (`paid_at + T+n` hari kerja) → **J-1** sudah
  terposting, creator masih `CREATOR_PAYABLE_PENDING`.
- Kalau ada transaksi `PAID` di CSV yang webhook-nya tidak masuk, lakukan
  rekonsiliasi order (tandai PAID) **sebelum** masuk langkah berikut.

### 5.2 Kapan mulai settlement
Saat di MAP dana **sudah withdrawable** dan admin **mencairkan ke rekening bank**
(aksi admin; bisa full sweep atau sebagian, lihat §5.7). Setelah dana masuk (mutasi
bank), barulah buat & konfirmasi batch.

### 5.3 Buat batch — pilih transaksi otomatis (default)

**Default / steady state — auto-match by rule.** Admin **tidak** menunjuk order.

1. Admin cukup mencatat pencairan: provider, nominal bukti (`actual_amount`),
   tanggal, evidence, dan periode/cutoff.
2. Sistem memilih **semua** transaksi yang memenuhi:
   - `payments.provider_id = batch.provider_id`
   - `payments.status = 'PAID'`
   - `payments.settlement_id IS NULL`
   - `payments.expected_settlement_date <= :cutoff` (cutoff = tanggal withdrawable)
3. `expected_amount` = Σ `payments.expected_settlement_amount` dari transaksi itu.
4. Pakai index `ix_payments_unsettled`.

Ini satu operasi **set-based** (query/`UPDATE ... WHERE rule`), jadi jumlah baris
tidak masalah: satu pencairan tetap satu batch. Lihat §5.8.

**Pengecualian — daftar order eksplisit.** Dipakai **hanya** kalau PG mencairkan
sebagian (bukan full sweep) atau tidak FIFO, misalnya dari disbursement report.

1. Ambil daftar `Order ID` dari sumber pencairan.
2. `POST /api/v1/settlements`:
   ```json
   {
     "providerCode": "MIDTRANS",
     "settlementTarget": "BANK",
     "actualAmount": 62240,
     "actualSettledAt": "2026-10-06T10:00:00+07:00",
     "evidenceSource": "BANK_STATEMENT",
     "evidenceReference": "BCA/2026-10-06/…",
     "periodStart": "2026-10-05",
     "periodEnd": "2026-10-06",
     "orderIds": ["order1", "order2", "order14", "01a10c58-…-88f6927869bd"]
   }
   ```
3. Sistem mencari `payment_attempts.provider_reference_id IN orderIds`, memfilter
   `payments.status=PAID AND payments.settlement_id IS NULL AND provider=MIDTRANS`.

> **Rekomendasi**: pakai **full sweep** setiap kali mencairkan (§5.7) supaya daftar
> transaksi deterministik dan admin tidak pernah perlu menunjuk order. Mode daftar
> eksplisit adalah fallback, bukan jalur utama.

> **Jangan** memakai cutoff = hari ini kalau T+n belum akurat — bisa me-*release*
> dana creator lebih cepat dari uang masuk bank. Cara mencocokkan pencairan lump-sum
> dengan ledger: §5.7.

### 5.4 Verifikasi sebelum konfirmasi
- `expected_amount` = Σ `payments.expected_settlement_amount` dari transaksi terpilih.
- Bandingkan dengan `actual_amount` (mutasi bank). Selisih = `variance_amount`.
- Jika selisih ≠ 0 karena **fee PG** (lihat §7), jangan asal timpa ke variance.

### 5.5 Konfirmasi
- `POST /api/v1/settlements/{id}/confirm`:
  - set `status=CONFIRMED`, `actual_amount`, `actual_settled_at`, `variance_amount`;
  - set `payments.settlement_id = batch.id`, `payments.settled_at`;
  - posting **J-2** (per batch) lalu **J-3** (per creator) via
    `LedgerApi.postJournal`, idempoten.

### 5.6 Sesudahnya
- Payment yang tidak ikut batch tetap `PAID` dan muncul di monitor OVERDUE.
- Batch bisa `CANCELLED` selama belum ada dana masuk (tanpa jurnal).

---

### 5.7 Full sweep vs withdraw sebagian (cutoff, bukan `paid_at`)

Kasusnya: admin withdraw dari PG dan dapat **satu angka** (mis. Rp30.000), sementara
di aplikasi ada mis. 10 transaksi `PAID`. Kuncinya: **yang menentukan transaksi mana
yang ikut adalah batas withdrawable, bukan `paid_at` mentah**. `paid_at` hanya dasar
perhitungan T+n; yang dipakai memfilter adalah `expected_settlement_date`.

Contoh (cutoff = 6 Okt, semua creator beda-beda nominal):

| Order | expected_settlement_date | expected_settlement_amount | Ikut batch? |
|-------|--------------------------|----------------------------|-------------|
| `order1` | 2026-10-06 | 20.000 | ✅ |
| `order2` | 2026-10-06 | 10.000 | ✅ |
| `order3` | 2026-10-07 | 20.000 | ❌ (masih pending) |

Σ kandidat (`expected_settlement_date <= cutoff`) = 30.000 = angka withdraw → **cocok**,
konfirmasi batch berisi `order1`+`order2`. `order3` menunggu batch berikutnya.

Aturan praktis:

1. **Selalu sweep seluruh withdrawable balance** (jangan withdraw sebagian). Kalau
   full sweep, transaksi yang ikut = *semua* transaksi `PAID`, provider cocok,
   `settlement_id IS NULL`, dan `expected_settlement_date <= cutoff`. Maka Σ
   `expected_settlement_amount` **harus** = angka withdraw. Deterministik, tanpa
   perlu memilih order.
2. Kalau **Σ ≠ angka withdraw**, jangan asal pilih yang kira-kira. Cek berurutan:
   - cutoff meleset 1 hari (libur/Sabtu-Minggu/T+n salah) → geser cutoff;
   - ada transaksi `PAID` di CSV tapi belum `PAID` di aplikasi (webhook tidak masuk)
     → rekonsiliasi dulu;
   - fee PG: pastikan charge pakai `gross_amount = gross + pg_fee` (§7), kalau tidak
     yang masuk bank net dan selisihnya bukan variance;
   - withdraw sebagian → minta daftar transaksi dari disbursement report provider.
3. **Hindari** mencocokkan dengan cara FIFO "akumulasi sampai pas" kalau bisa.
   Itu heuristik rapuh: kalau provider tidak mencairkan FIFO, atau ada hold, kamu
   bisa salah menandai transaksi mana yang sudah cair. Cukup aman untuk *release*
   creator (semua `PAID`, tiap payment sekali, tidak dobel), tapi membuat
   `variance_amount` pada batch jadi menyesatkan.
4. `paid_at` hanya tie-breaker: kalau beberapa transaksi punya
   `expected_settlement_date` sama, urutkan `paid_at` untuk mencocokkan dengan
   urutan di laporan provider.

> Kesimpulan: bank mutation = **nominal**; cutoff (`expected_settlement_date`) =
> **transaksi mana yang ikut**. Dua-duanya harus ketemu supaya `variance_amount = 0`.

---

### 5.8 Skala: ratusan ribu transaksi tidak menambah kerja admin

Kekhawatiran yang wajar: "kalau settled harus manual, bagaimana kalau ada ratusan
ribu transaksi?" Jawabannya: **yang besar adalah jumlah transaksi, bukan jumlah
batch**. Admin tidak pernah menyentuh transaksi satu per satu.

| Hal | Skala |
|-----|-------|
| Pencairan dari PG (aksi admin + mutasi bank) | beberapa per hari/minggu |
| Batch `settlements` | **1 per pencairan**, bukan per transaksi |
| Pemilihan transaksi | 1 query/`UPDATE` set-based (`WHERE <rule>`) |
| Verifikasi admin | 1 angka: `expected_amount` vs `actual_amount` |
| Posting jurnal | J-2 **1 per batch**; J-3 **per creator** (bisa di-chunk) |

Jadi kerja admin per pencairan tetap: "apakah total yang masuk bank = total yang
sistem hitung?" Tidak ada pemilihan per-transaksi.

Catatan implementasi (Milestone 3):

- `UPDATE payment.payments SET settlement_id=…, settled_at=… WHERE <rule>` adalah
  operasi set-based; aman untuk ratusan ribu baris. Bisa di dalam transaksi batch
  yang sama, atau di-chunk per rentang.
- **J-3 per creator**: jumlah creator bisa besar. Karena `journals.idempotency_key`
  unik, satu J-3 per creator **tidak boleh** memakai key yang sama. Gunakan key
  per creator, mis. `SETTLEMENT:{settlementId}:RELEASE:{userId}`, atau tinjau
  agregasi release di ledger. Putuskan sebelum implementasi.
- Bila perlu, pecah pekerjaan release menjadi beberapa "shard" per provider/periode;
  `external_settlement_id` tetap satu sehingga audit tetap satu batch.
- **Ingest report PG** (adapter `evidence_source=REPORT_FILE`) dapat membuat batch
  `PENDING` otomatis (isi `external_settlement_id`, periode, nominal). Admin lalu
  hanya mengonfirmasi. Contoh CSV Midtrans sudah memuat `Order ID`, `Total Fee`, dan
  `Settlement time` untuk mengisi `pg_fee_amount` dan mendeteksi order yang webhook-nya
  tidak masuk.

> Prinsip audit bersih: **apapun cara PG mencairkan**, ledger hanya bergerak saat
> batch `CONFIRMED` dengan bukti. Invariant yang dijaga: `actual_amount =
> expected_amount + variance_amount`, dan setiap batch punya `evidence_source` +
> `evidence_reference`.

---

## 6. Jurnal yang diposting

Notasi: amount bulat IDR. Idempotency key: `SETTLEMENT:{settlementId}:CONFIRMED`
(J-2) dan `SETTLEMENT:{settlementId}:RELEASE:{creatorUserId}` (J-3). Lihat
[`../ledger/ledger-example.md`](../ledger/ledger-example.md) J-2 & J-3.

### J-2 — dana PG → bank platform (per batch)
```
DEBIT   BANK_OPERATING (1200, BANK-1)            Σ expected
CREDIT  PG_CLEARING_RECEIVABLE (1100, PROVIDER)  Σ expected
```
Kalau `settlement_target = PROVIDER_BALANCE`, debit-nya `PAYIN_PROVIDER_BALANCE`
(1150) dan bukan `BANK_OPERATING`.

### J-3 — release hak creator (per creator, di dalam batch yang sama)
```
DEBIT   CREATOR_PAYABLE_PENDING   (2100, USER)   Σ net creator (creator itu)
CREDIT  CREATOR_PAYABLE_AVAILABLE (2110, USER)   Σ net creator (creator itu)
```

> Satu batch bisa memuat banyak creator. Karena idempotency key J-3 harus unik per
> jurnal, key per creator perlu disuffiks `:{userId}` (lihat §5.8).

Untuk batch 4 transaksi di atas, misal tiap donasi 20.000 dengan net creator
17.780 (platform fee 2.000 + PPN 220) dan semuanya creator sama:
```
J-2: DEBIT BANK_OPERATING 80.000 ; CREDIT PG_CLEARING_RECEIVABLE 80.000
     (assuming expected_settlement_amount = gross = 20.000 × 4)
J-3: DEBIT CREATOR_PAYABLE_PENDING 71.120 ; CREDIT CREATOR_PAYABLE_AVAILABLE 71.120
```

---

## 7. Basis nominal — fee PG DITANGGUNG DONATUR (diputuskan)

**Keputusan**: fee PG *pass-through* ke donatur. Skema `V5` sudah benar:
`total_charged_amount = gross + pg_fee` dan `expected_settlement_amount = gross`
(constraint `ck_payment_amounts`). Fee PG **tidak masuk ledger**.

Contoh untuk donasi 20.000:

```
gross (donasi)            = 20.000
pg_fee = 4.000 + PPN 11%  =  4.440   (4.000 + 440; PPN = 11%, bukan 4%)
total_charged (donatur)   = 24.440   (inilah gross_amount yang dikirim ke Midtrans)
yang masuk saldo platform = 20.000   (Midtrans ambil 4.440)
```

Konsekuensi:
- Ledger **J-1**: `DEBIT PG_CLEARING_RECEIVABLE 20.000` (gross) ; credit creator
  pending + revenue + PPN platform.
- Ledger **J-2**: `DEBIT BANK_OPERATING Σgross ; CREDIT PG_CLEARING_RECEIVABLE Σgross`
  — pakai **gross 20.000**, bukan net.
- CSV: `Amount` seharusnya **24.440** (total charged), `Total Fee` `-4.440`, dan
  jumlah yang masuk saldo = `Amount + Total Fee = 20.000`.

> **Data CSV contoh adalah charge test yang salah**: `Amount` di sana 20.000
> (fee PG tidak ditambahkan), sehingga MAP menampilkan net `15.560`. Itu bug
> konfigurasi charge, **bukan** bug skema. Di produksi, pastikan charge Midtrans
> memakai `gross_amount` = donasi + fee PG.

Checklist produksi:
1. Charge Midtrans: `gross_amount = gross + pg_fee` (mis. 24.440). Kalau tidak,
   saldo MAP akan net dan platform menanggung fee (rugi).
2. `expected_settlement_amount = gross` (sudah sesuai constraint).
3. `pg_fee_amount` harus benar. Webhook Midtrans sering **tidak** membawa fee;
   CSV harian `Total Fee` yang membawanya → perlu langkah ingest report untuk
   meng-update `pg_fee_amount` (dan verifikasi nominal) sebelum settlement.
4. Jangan pernah membukukan fee PG ke `PG_FEE_EXPENSE` (5000) selama model
   pass-through ini dipakai.

---

## 8. Multiple payment gateway

Prinsip: **satu batch = satu provider = satu bukti**. Tidak ada batch campur PG.

1. **Filter wajib**: semua query transaksi & pencarian `Order ID` difilter
   `provider_id`. `provider_reference_id` unik per `(channel_route_id, …)`, jadi
   aman dipakai lintas PG.
2. **Rute & aturan beda**: `settlement_delay_days` dan `settlement_target` per
   `channel_route`; evidence source bisa beda (Midtrans = `REPORT_FILE`/
   `BANK_STATEMENT`, PG lain mungkin `API`).
3. **Adapter**: PG yang punya disbursement report/webhook boleh mengisi batch yang
   **sama** lewat `internal/provider/*Client`. Alur internal (match → verify →
   confirm → J-2/J-3) tidak berubah.
4. **Bank account**: pastikan `settlement_target`/rekening bank per provider jelas
   (mis. satu rekening operasional menerima banyak PG, atau rekening terpisah).
5. **Reconciliation**: jalankan `reconciliation_runs` per provider (mis.
   bandingkan Σ order yang kita match vs laporan PG) untuk mendeteksi order yang
   tidak pernah tercairkan — ini yang menjaga audit tetap bersih ketika cara PG
   berbeda-beda.

---

## 9. Idempotency & koreksi

- `payments.settlement_id` mencegah 1 payment masuk 2 batch.
- Confirm ulang batch yang sudah `CONFIRMED` harus no-op (idempotent), bukan
  dobel jurnal (karena `idempotency_key` unik di `journals`).
- Koreksi kesalahan settlement: **jangan** update/delete jurnal; buat jurnal
  reversal (`reverses_journal_id`) atau adjustment maker-checker.

---

## 10. Status implementasi & yang masih perlu diputuskan

Belum diimplementasikan (ada di [`todo.md`](./todo.md) Milestone 3/5):

- `SettlementRepository`, `SettlementService`, endpoint admin, auto-match, job OVERDUE.
- **Ingest CSV** (parsing `Order ID`, `Amount`, `Total Fee`, `Settlement time`) untuk
  mem-verifikasi/mengisi `pg_fee_amount` dan mendeteksi order yang belum `PAID` —
  **bukan** untuk memilih order per transaksi.
- **Pastikan charge memakai `gross_amount = gross + pg_fee`** (fee ditanggung
  donatur, §7) — kalau tidak, MAP net dan nominal `expected_settlement_amount`
  tidak cocok.
- Penyesuaian `settlement_delay_days` agar sesuai aturan withdrawable MAP.
- Strategi J-3 saat jumlah creator besar (key per creator / chunking, §5.8).
- Opsional: simpan `Settlement time` per transaksi (mis. `payment_attempts.settled_at`
  / `payments.settled_at` dari report) supaya withdrawable bisa dihitung tepat,
  bukan sekadar T+n dari `paid_at`.
