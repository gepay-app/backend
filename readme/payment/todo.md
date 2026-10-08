# Payment Module — Rencana Kerja (Berurutan)

> Kerjakan **urut dari atas ke bawah**. Setiap milestone punya hasil yang bisa kamu lihat/uji.
> Belum lanjut ke milestone berikutnya sebelum milestone sekarang hijau.
>
> Sumber desain: [`payment-design.md`](./payment-design.md) · Alur dana: [`payment-example.md`](./payment-example.md) · Status: [`states.md`](./states.md) · Konvensi: [`AGENTS.md`](../../AGENTS.md).

---

## Status Sekarang

✅ Sudah ada (dikerjakan): migrasi `V5__payment_tables.sql` (batch settlement + kebijakan per route + holidays), seluruh entity, enum, `BusinessDayCalculator`, `Holiday`, `package-info` (`ledger::API`).
⬜ Belum ada: repository, service, facade `PaymentApi`, controller, adapter provider, error enum, i18n, test.

---

## Milestone 1 — Donasi Masuk & Dibayar (PAYIN)

**Hasil**: bikin donasi → bayar di sandbox Midtrans → status `PAID` + saldo creator `PENDING` muncul.

1. Repository: `ProviderRepository`, `ChannelRepository`, `ChannelRouteRepository`, `FeeConfigRepository`, `UserFeeOverrideRepository`, `PaymentRepository`, `PaymentAttemptRepository`, `ProcessedEventRepository`.
2. `PaymentError` (implements `ErrorCode`) + i18n `payment/` (`messages.properties` + `messages_id.properties`, key berprefix `payment.`).
3. `FeeResolver` — resolve rate card berversi (`fee_configs`) + override user; snapshot komponen fee; **policy pembulatan integer** (floor/half-up).
4. `internal/provider/PaymentProviderClient` (SPI) + `ProviderEvent` + `ProviderClientRegistry` + `MidtransPayinClient` (charge VA/QRIS, `verifySignature` SHA512 `order_id+status_code+gross_amount+serverKey`) + `FakePaymentProviderClient` (untuk test).
5. `api/PaymentApi` (facade) + DTO response (`PaymentResponse`, `PaymentAttemptResponse`).
6. `PaymentService.createDonation` — idempotensi `payments.idempotency_key`, pilih route (`channel_routes`), hitung fee, buat `Payment` + `PaymentAttempt`, panggil provider charge.
7. Controller: `POST /api/v1/payments`, `GET /api/v1/payments/{id}`.
8. Test: create donation, fee snapshot benar, idempotensi, modularity.

---

## Milestone 2 — Webhook PAID / EXPIRED

**Hasil**: webhook Midtrans diproses aman (idempoten), status berubah benar.

1. `WebhookController` (`/api/v1/webhooks/**`, `permitAll` + verifikasi signature) → adapter → `ProviderEvent` kanonik.
2. `WebhookService.handlePaid` — insert `processed_events` (unique `provider_id+external_event_id`) → lock `Payment` (`@Version` optimistic) → `markPaid` + attempt `PAID` → hitung `expected_settlement_date` (business day) → posting **J-1** via `LedgerApi`.
3. `handleExpired` / `handleFailed` / `handleCancelled` — transisi status + attempt.
4. Aturan recreate attempt: attempt `EXPIRED` boleh dibuatkan baru selama `Payment` belum `EXPIRED`.
5. Test: webhook dobel (hanya 1x efek), transisi ilegal ditolak, jurnal seimbang.

---

## Milestone 3 — Settlement Otomatis (Quartz) · mode portofolio

**Tujuan**: setiap hari pukul **03:00 Asia/Jakarta** satu job Quartz men-settle semua
payment `PAID` yang sudah melewati `expected_settlement_date` (T+n hari kerja),
lewat batch + jurnal yang tetap auditable. Hak creator `PENDING → AVAILABLE`.
Tanpa admin, tanpa CSV.

**Prasyarat**: Milestone 1 (donasi) & Milestone 2 (webhook PAID mengisi `paid_at`,
`expected_settlement_date`, dan mem-post J-1). Tanpa itu tidak ada payment `PAID`
untuk di-settle, dan J-3 akan mendebit akun yang belum bersaldo.

**Langkah (tujuan, bukan kode)**

1. Query kandidat: payment `status=PAID`, `settlement_id IS NULL`,
   `expected_settlement_date <= cutoff` (cutoff = **hari ini**). Kelompokkan per
   `provider_id` + `settlement_target` route.
2. Buat satu header `settlements` per grup per run; `expected_amount` =
   Σ `expected_settlement_amount`.
3. Konfirmasi tanpa bukti eksternal: tandai `evidence_source` penanda sistem,
   simpan snapshot asumsi di `raw_evidence` (cutoff, jumlah payment, Σ, versi
   aturan). `actual_amount = expected_amount`, `variance_amount = 0` — dicatat
   eksplisit sebagai asumsi portofolio.
4. Tandai tiap payment `settlement_id` + `settled_at` (sekali saja).
5. Posting jurnal via `LedgerApi`, idempoten:
   - J-2 per batch `SETTLEMENT:{id}:CONFIRMED` (dana PG → bank).
   - J-3 per creator `SETTLEMENT:{id}:RELEASE:{userId}` (`PENDING → AVAILABLE`).
6. Job Quartz: `@DisallowConcurrentExecution`, cron harian 03:00 Asia/Jakarta
   (override via env), misfire fire-and-proceed; cutoff `<= hari ini` memberi
   catch-up otomatis.
7. Test: hitung hari kerja (T+n + libur), seleksi kandidat, idempotensi (run dobel
   ≠ jurnal dobel), jurnal J-2/J-3 seimbang, agregasi per creator.

**Tujuan audit (WAJIB walau otomatis)**

- Ledger tetap **append-only** & **balanced**; koreksi hanya lewat jurnal
  reversal/adjustment — jangan pernah edit/hapus.
- Setiap run meninggalkan jejak: baris `settlements` + `raw_evidence` + description
  jurnal yang menandai asumsi sistem.
- Sediakan jalur rekonsiliasi (walau belum diimplementasi): saat data PG asli
  (CSV/SFTP/email) datang → `reconciliation_runs`/`items`, selisih → adjustment.
  Tanpa ini `variance_amount` selalu 0 dan selisih nyata (fee/hold/partial) tak
  terdeteksi.
- Idempotency key per batch **dan** per creator (J-3).

**Sengaja di-skip (dengan catatan)**: ingest CSV/email/SFTP, endpoint admin,
otomatis-match order, bukti nyata. Produksi sesungguhnya **wajib** rekonsiliasi
sebelum release; mode ini hanya demonstrasi pola.

---

## Milestone 4 — Withdrawal & Payout

**Hasil**: creator tarik dana sampai sukses/gagal via Flip.

1. `PayoutDestinationRepository`, `WithdrawalRepository`, `PayoutRepository`.
2. CRUD `payout_destinations` (ownership check via `CurrentUser`).
3. `WithdrawalService.create` — idempotensi, cek saldo `LedgerApi.getBalanceAmount(CREATOR_PAYABLE_AVAILABLE, userId)` vs amount+fee, snapshot tujuan, fee `PLATFORM_WITHDRAWAL`, posting **J-5** (HOLD).
4. `PayoutProviderClient` (SPI) + `FlipPayoutClient` (disburse + webhook `PG_FLIP_VALIDATION_KEY`).
5. `PayoutService.processPayout` (Quartz) — sukses → **J-6** + withdrawal `PAID`; gagal → **J-7** (rollback hold) + withdrawal `FAILED`.
6. Test e2e: donation → paid → settlement → withdraw → payout; verifikasi saldo ledger tiap step.

---

## Milestone 5 — Kelengkapan (lanjutan)

- Refund (full + partial; model reversal) + jurnal.
- Chargeback (review arah J-11 di readme lama sebelum implementasi).
- Adjustment maker-checker + jurnal.
- `UserFeeOverride` / `FeeConfig` admin.
- Reconciliation penuh (ingest report ↔ internal).
- OpenAPI/dokumentasi.

---

## Catatan Teknis (jangan sampai lupa)

- **Jurnal selalu balanced**; posting lewat `LedgerApi.postJournal` dengan `idempotencyKey` `{TYPE}:{ID}:{ACTION}`.
- **Webhook `permitAll` tapi wajib verifikasi signature** (jangan percaya payload).
- **Tidak ada `@Scheduled`** — pakai Quartz (cluster-safe).
- `payments.provider_id` snapshot diisi dari route saat create.
- `order_id` Midtrans = `payment_attempts.id` (UUID v7) — kunci matching webhook (dan report PG bila nanti dipakai) 1:1.
- Hari libur nasional di-maintain di `payment.holidays` tiap tahun (SKB).
- **Settlement otomatis**: job Quartz harian 03:00 men-settle payment `PAID` saat
  `expected_settlement_date` (`paid_at` + T+n **hari kerja**) terlewati; cutoff
  `<= hari ini` sehingga settle tepat T+n (**tanpa** tambahan hari). Bacaan:
  [`payment-design.md` §5](./payment-design.md).
- **Basis nominal**: **fee PG ditanggung donatur**, jadi charge wajib memakai
  `gross_amount = gross + pg_fee`; `expected_settlement_amount = gross`.
- **Skala settlement**: pemilihan transaksi set-based, 1 batch per grup
  (provider + `settlement_target`). J-3 diposting **per creator**; saat creator
  banyak, pakai key idempoten per creator (`…:RELEASE:{userId}`) dan chunk.
- **Audit**: ledger append-only; tiap batch meninggalkan `settlements` +
  `raw_evidence`. Mode portofolio (`variance = 0`) **bukan** pengganti rekonsiliasi
  produksi (CSV/SFTP/email).
