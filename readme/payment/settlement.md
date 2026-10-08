# Payment — Settlement Otomatis (Quartz)

> Ini **portofolio**, bukan produksi: tidak ada rekening bank nyata, dana hanya hidup
> di sandbox PG (payin Midtrans, payout Flip). Karena itu settlement tidak menunggu
> bukti mutasi bank — cukup ikut waktu T+n hari kerja.
>
> Konsep "settlement 3 arti": [overview.md §2](./overview.md) · Jurnal J-2/J-3:
> [Ledger — Contoh](../ledger/examples.md).

---

## 1. Cara kerja

1. `payments.expected_settlement_date` = `paid_at` + T+n **hari kerja**
   (`channel_routes.settlement_delay_days`; Sabtu/Minggu & libur nasional dilewati,
   dihitung `BusinessDayCalculator` + tabel `payment.holidays`).
2. Job Quartz (`SettlementJob`) jalan **tiap hari 03:00 Asia/Jakarta**, memilih
   payment `status=PAID`, `settlement_id IS NULL`, `expected_settlement_date <= hari ini`.
3. Kandidat dikelompokkan per `provider_id` + `settlement_target` (dari route);
   tiap grup menjadi **satu** header `settlements`.
4. Batch langsung `CONFIRMED` dengan `actual_amount = expected_amount` dan
   `variance_amount = 0`, `evidence_source` penanda sistem, snapshot asumsi di
   `raw_evidence`. Ini **asumsi**, bukan bukti.
5. Konfirmasi → posting **J-2** + **J-3** lewat `LedgerApi`, lalu tandai tiap payment
   `settlement_id` + `settled_at`.

Karena cutoff `<= hari ini`, job men-settle tepat pada tanggal T+n — **tanpa** tambahan
hari. Kalau server sempat mati, run berikutnya otomatis mengejar (catch-up).

---

## 2. Jadwal & idempotency

- Cron `payment.settlement.cron` (default `0 0 3 * * ?`, timezone Asia/Jakarta).
- Job `@DisallowConcurrentExecution`; misfire = fire-and-proceed.
- Aman dijalankan dobel: payment yang sudah punya `settlement_id` tidak ikut lagi, dan
  `journals.idempotency_key` unik mencegah jurnal dobel.

---

## 3. Audit (wajib walau otomatis)

- Ledger tetap **append-only & seimbang**; koreksi hanya lewat jurnal reversal/adjustment.
- Setiap run meninggalkan jejak: baris `settlements` + `raw_evidence` + description
  jurnal yang menandai asumsi sistem.
- Jalur rekonsiliasi (CSV/SFTP/email) disiapkan menyusul: saat data PG asli datang,
  bandingkan dengan batch lewat `reconciliation_runs`/`items`; selisih → adjustment.

---

## 4. Catatan produksi

Di sistem nyata, pemicu konfirmasi batch adalah **bukti dana masuk** (mutasi bank / report
PG via SFTP/CSV/email), bukan waktu. Jurnal J-2/J-3 **tidak berubah** — hanya pemicunya.
Selama mode portofolio, `variance_amount` selalu 0 dan selisih nyata (fee/hold/partial)
memang tidak terdeteksi. Risiko yang diterima: dana creator di-release sebelum uang
benar-benar masuk bank.
