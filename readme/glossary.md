# Glosarium — Bahasa Manusia

> Istilah yang dipakai di project ini, dijelaskan untuk yang **bukan akuntan**.
> Daftar akun & arti lengkap: [Chart of Accounts](./ledger/chart-of-accounts.md).
> Kembali ke [pintu masuk](./README.md).

---

## Bagian A — Dasar akuntansi

### Ledger (buku besar)

Bayangkan **buku tabungan untuk setiap pihak**: pembayar, creator, Midtrans, Flip, bank,
platform. Setiap kali uang bergerak, dicatat satu baris di buku yang relevan. Bedanya
dengan saldo biasa: ledger menyimpan **riwayat lengkap**, bukan hanya angka akhir.

### Debit / Kredit

Dua **kolom** di buku besar. **Bukan** "tambah" dan "kurang".

- Menambah aset (mis. uang di bank) = **debit**.
- Menambah utang/hak orang lain (mis. kewajiban ke creator) = **kredit**.

Karena tiap gerakan menyentuh minimal dua buku, satu debit selalu berpasangan dengan
satu kredit bernilai sama. Itu sebabnya disebut **double-entry**.

### Jenis akun

| Jenis | Arti | Saldo tumbuh saat | Contoh |
|---|---|---|---|
| Aset | yang kita miliki / hak kita | debit | uang di bank, piutang ke Midtrans |
| Liabilitas | yang kita utang | kredit | hak creator yang belum dibayar |
| Pendapatan | penghasilan kita | kredit | fee platform |
| Beban | biaya kita | debit | fee payout provider |

### Jurnal, entry, idempotency

- **Jurnal** = satu kejadian, punya `idempotency_key` unik.
- **Entry** = satu baris debit/kredit di dalam jurnal (minimal 2 baris).
- **Idempotency key** = penanda unik satu kejadian (mis. `PAYMENT:PAY-1:PAID`). Kalau
  request/webhook datang dua kali dengan key sama, yang kedua diabaikan.
- **Append-only & reversal** = riwayat tidak pernah diubah/dihapus; koreksi dibuat
  sebagai jurnal pembalikan baru.

---

## Bagian B — Istilah project

### Payin / Payout

- **Payin** = uang masuk ke platform (pembayaran pengguna; mis. donasi).
- **Payout** = uang keluar dari platform (dikirim ke creator).

### Provider / Channel / Route

- **Provider** = penyedia layanan, mis. Midtrans (payin), Flip (payout).
- **Channel** = cara bayar netral, mis. `VA_BCA`, `QRIS`.
- **Route** = "channel X dilayani provider Y dengan kebijakan Z".

### Pending / Available / Hold

Tiga kondisi hak creator:

- **Pending** = sudah berhak, tapi uang belum cair → belum boleh ditarik.
- **Available** = uang sudah cair → boleh ditarik.
- **Hold** = creator sedang menarik; uang dikunci agar tidak dipakai ganda.

### Settlement punya 3 arti

Ini sumber kebingungan terbesar:

| # | Arti | Sumber | Peran di sistem |
|---|---|---|---|
| A | Status Midtrans `settlement`: uang sudah masuk **saldo Midtrans** | webhook/CSV | penanda `payments.status=PAID` |
| B | **Withdrawable di PG**: saldo PG boleh ditarik ke bank | dashboard MAP | cuma pantauan |
| C | **Settlement kita**: uang dianggap cair | job Quartz (T+n); bukti di-skip untuk portofolio | memicu **J-2 & J-3** |

Midtrans pakai "settlement" untuk **A**; kita pakai **settled** untuk **C**.

### Settled (kita)

Transaksi sudah masuk batch settlement `CONFIRMED` → hak creator pindah
`PENDING → AVAILABLE` → bisa ditarik. Disimpan di `payments.settlement_id` +
`payments.settled_at`.

### T+n hari kerja

Waktu kapan dana PG dianggap bisa ditarik, dihitung **n hari kerja** dari `paid_at`
(Sabtu/Minggu & libur nasional dilewati). Di mode portofolio, tanggal ini **memicu**
job Quartz men-settle.

### Evidence (bukti)

Di produksi, settlement wajib dipicu bukti: `API`, `REPORT_FILE` (CSV),
`BANK_STATEMENT` (mutasi bank), atau `MANUAL`. **Mode portofolio** tidak memakai bukti
ini — dipicu waktu dan ditandai penanda sistem.

### Batch settlement

Satu header `payment.settlements` = satu pencairan dari satu provider. Berisi
`expected_amount`, `actual_amount`, `variance_amount`. 1 payment masuk 1 batch.

### Variance

Selisih `actual_amount − expected_amount`. Idealnya 0.

### Mode portofolio

Build ini untuk **portofolio**, bukan produksi: tidak ada rekening bank nyata, dana hanya
di sandbox PG. Settlement dipicu **waktu (T+n)**, bukan bukti. Jurnal tetap auditable,
tapi `variance` selalu 0 dan selisih nyata tidak terdeteksi.

### Vendor-blind & adapter

Modul bisnis tidak menyebut nama vendor. Bahasa vendor diterjemahkan oleh **adapter**
(mis. `MidtransPayinClient`, `FlipPayoutClient`), jadi tambah PG = tambah adapter.

### Inbox / `processed_events`

Tabel pengaman: webhook yang sama datang dua kali tetap diproses sekali.

### Ledger tidak di-cache

Saldo selalu dibaca dari database agar tidak menyajikan angka basi. Ini pengecualian
sadar dari kebijakan cache umum.
