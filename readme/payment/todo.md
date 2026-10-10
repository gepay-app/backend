# Payment — Rencana Kerja

> Kerjakan **urut dari atas ke bawah**. Satu milestone harus **hijau (test lolos)**
> sebelum lanjut. Setelah tiap milestone, update status di sini.
>
> Konsep: [overview.md](./overview.md) · Status: [states.md](./states.md) ·
> Settlement: [settlement.md](./settlement.md) · Contoh: [example.md](./example.md) ·
> Konvensi: [`../../AGENTS.md`](../../AGENTS.md).

---

## Status sekarang

- ✅ Migrasi `V5__payment_tables.sql` (batch settlement, kebijakan per route, holidays),
  semua entity + enum, `BusinessDayCalculator`, `Holiday`, `package-info`.
- ✅ **Milestone 1** — repository, `PaymentError` + i18n, `FeeResolver` (snapshot + pembulatan
  half-up), adapter `MidtransPayinClient`, `api/PaymentApi` + DTO, `PaymentService.createPayment`
  (idempoten, `order_id = payment_attempts.id`), `PaymentController`; unit test.
- ✅ **Milestone 2** — `WebhookController` → Redis Stream → `PaymentWebhookService`:
  inbox `processed_events`, `expected_settlement_date` (T+n hari kerja), posting **J-1**
  via `LedgerApi`, status `PAID`/`EXPIRED`/`FAILED`; unit test (jurnal seimbang).
- ✅ **Milestone 3** — settlement otomatis: `SettlementService`/`SettlementWriter`
  (group per `provider_id`+`settlement_target`, batch `CONFIRMED` `evidence_source=SYSTEM`),
  `SettlementJob` (Quartz, cron 03:00 Asia/Jakarta), posting **J-2** + **J-3** per creator;
  unit test (idempoten, J-2/J-3 seimbang, agregasi per creator).
- ✅ **Milestone 4** — withdrawal & payout: `WithdrawalApi`/`WithdrawalService`
  (CRUD `payout_destinations` ownership via `CurrentUser`, hold **J-5**),
  `FlipPayoutClient` (disburse `POST /v3/disbursement`), `PayoutService`/`PayoutWriter`
  + `PayoutJob` (Quartz), posting **J-6**/**J-7**; unit test (saldo, idempotensi, jurnal seimbang).
- ⬜ Milestone 5 (refund/chargeback/reconciliation/adjustment/OpenAPI) — ditunda.

---

## Cara kerja yang disarankan (best practice)

1. **Urutan per milestone**: repository → error enum + i18n → service (`@Transactional`)
   → facade `PaymentApi` → controller → test. Jangan mulai dari controller.
2. **`@Transactional` hanya di service**, bukan controller/repository.
3. **Idempotency key dari payment** (`TIPE:ID:AKSI`), bukan dibuat ledger.
4. **Verifikasi signature webhook** — jangan pernah percaya payload mentah.
5. **Uang = `BIGINT`** (rupiah bulat). Jangan pakai `double`/`float`.
6. **Enum disimpan sebagai `varchar`** — jangan bikin tipe enum di Postgres.
7. **ID user-facing pakai UUID v7** (`UuidCreator.getTimeOrderedEpoch()`).
8. **Tanpa `@Scheduled`** — semua penjadwalan lewat Quartz.
9. **Error lewat `PaymentError implements ErrorCode`** + i18n `payment.*`; `code` =
   message key (public API, jangan diganti sembarangan).
10. **Jurnal selalu seimbang**; posting hanya lewat `LedgerApi.postJournal`.
11. **Jalankan `./mvnw test`** tiap milestone (termasuk `ModularityTests`).

---

## Milestone 1 — Pembayaran masuk & dibayar (PAYIN)

**Tujuan**: bikin **pembayaran** (generik, pakai `type`) → bayar di sandbox Midtrans →
status `PAID` + hak penerima `PENDING` muncul (J-1). Donasi/pembelian konten **bukan**
tanggung jawab payment — itu consumer terpisah yang nanti memanggil `payment::api`.

**Definition of Done**: bisa `createPayment` via API, fee ter-snapshot benar, idempoten,
`ModularityTests` hijau.

Langkah:

1. Repository: `ProviderRepository`, `ChannelRepository`, `ChannelRouteRepository`,
   `FeeConfigRepository`, `UserFeeOverrideRepository`, `PaymentRepository`,
   `PaymentAttemptRepository`, `ProcessedEventRepository`.
2. `PaymentError` (implements `ErrorCode`) + i18n `payment/` (`messages.properties` +
   `messages_id.properties`, prefix `payment.`).
3. `FeeResolver` — resolve rate card ber-versi + override user; snapshot komponen fee;
   **policy pembulatan integer** (floor/half-up) dipilih & didokumentasikan.
4. Adapter provider: `PaymentProviderClient` (SPI) + `IncomingProviderNotification` +
   `ProviderClientRegistry` + `MidtransPayinClient` (charge VA/QRIS, `verifySignature`
   SHA512 `order_id+status_code+gross_amount+serverKey`) + `FakePaymentProviderClient`.
5. `api/PaymentApi` (facade) + DTO (`PaymentResponse`, `PaymentAttemptResponse`).
6. `PaymentService.createPayment` — idempotensi `payments.idempotency_key`, pilih route,
   hitung fee, buat `Payment` + `PaymentAttempt`, panggil charge.
7. Controller: `POST /api/v1/payments`, `GET /api/v1/payments/{id}`.
8. Test: create payment, fee snapshot benar, idempotensi.

---

## Milestone 2 — Webhook PAID / EXPIRED

**Tujuan**: webhook Midtrans diproses aman (idempoten) dan status berubah benar.

**Definition of Done**: webhook dobel hanya berefek sekali; transisi ilegal ditolak;
jurnal seimbang.

Langkah:

1. `WebhookController` (`/api/v1/webhooks/**`, `permitAll` + verifikasi signature) →
   adapter → `IncomingProviderNotification` kanonik.
2. `WebhookService.handlePaid` — insert `processed_events` (unique
   `provider_id+external_event_id`) → lock `Payment` (`@Version`/optimistic) →
   `markPaid` + attempt `PAID` → hitung `expected_settlement_date` (hari kerja) →
   posting **J-1** via `LedgerApi`.
3. `handleExpired` / `handleFailed` / `handleCancelled`.
4. Aturan recreate attempt: attempt `EXPIRED` boleh dibuat baru selama `Payment` belum
   `EXPIRED`.
5. Test: webhook dobel, transisi ilegal, jurnal seimbang.

---

## Milestone 3 — Settlement otomatis (Quartz) · mode portofolio

**Tujuan**: tiap hari 03:00 job Quartz men-settle payment `PAID` yang lewat
`expected_settlement_date`; creator `PENDING → AVAILABLE`; tanpa admin/CSV.

**Prasyarat**: Milestone 1 & 2 (tanpa `PAID`, tidak ada yang di-settle).

**Definition of Done**: job memilih kandidat benar, batch + J-2/J-3 terposting idempoten,
`raw_evidence` terisi, saldo creator benar.

Langkah:

1. Repository query kandidat: `PAID`, `settlement_id IS NULL`,
   `expected_settlement_date <= hari ini`; kelompokkan `provider_id` + `settlement_target`.
2. `SettlementService` (`@Transactional` per grup): buat header `settlements`,
   `expected_amount` = Σ `expected_settlement_amount`.
3. Konfirmasi tanpa bukti eksternal: `evidence_source` penanda sistem, snapshot asumsi
   di `raw_evidence`; `actual = expected`, `variance = 0`.
4. Tandai tiap payment `settlement_id` + `settled_at` (sekali saja).
5. Posting **J-2** (`SETTLEMENT:{id}:CONFIRMED`) + **J-3** per creator
   (`SETTLEMENT:{id}:RELEASE:{userId}`).
6. `SettlementJob` (`@DisallowConcurrentExecution`) + scheduler cron 03:00 Asia/Jakarta
   (override via env), misfire fire-and-proceed.
7. Test: hitung hari kerja, seleksi kandidat, idempotensi, J-2/J-3 seimbang, agregasi
   per creator.

Detail: [settlement.md](./settlement.md).

---

## Milestone 4 — Withdrawal & payout

**Tujuan**: creator tarik dana sampai sukses/gagal via Flip.

**Definition of Done**: e2e pembayaran → settle → withdraw → payout; saldo ledger benar tiap
step.

Langkah:

1. Repository: `PayoutDestinationRepository`, `WithdrawalRepository`, `PayoutRepository`.
2. CRUD `payout_destinations` (ownership check via `CurrentUser`).
3. `WithdrawalService.create` — idempotensi, cek saldo
   `LedgerApi.getBalanceAmount(CREATOR_PAYABLE_AVAILABLE, userId)` vs amount+fee,
   snapshot tujuan, fee `PLATFORM_WITHDRAWAL`, posting **J-5**.
4. `PayoutProviderClient` (SPI) + `FlipPayoutClient` (disburse + webhook
   `PG_FLIP_VALIDATION_KEY`).
5. `PayoutService.processPayout` (Quartz) — sukses → **J-6** + withdrawal `PAID`;
   gagal → **J-7** + withdrawal `FAILED`.
6. Test e2e.

---

## Milestone 5 — Kelengkapan (lanjutan)

- Refund (full + partial; model reversal) + **J-8/J-9**.
- Chargeback (**J-11**) — lihat catatan `JournalReferenceType` di bawah.
- Adjustment maker-checker + **J-10**.
- `UserFeeOverride` / `FeeConfig` admin.
- Reconciliation penuh (ingest report ↔ internal).
- OpenAPI/dokumentasi.

---

## Catatan teknis & kesalahan yang harus dihindari

- **Payment = engine generik, bukan fitur donasi.** Jangan taruh aturan donasi/konten di
  `payment`. Donasi/pembelian konten nanti jadi modul consumer terpisah (`donation`) yang
  depends ke `payment::api`.
- **`type` adalah kode `String` dari consumer** (mis. `"DONATION"`), bukan enum domain di
  `payment.api`. `payment` hanya menyimpannya sebagai label; jangan tambahkan enum
  `DONATION`/`CONTENT_PURCHASE` kembali ke payment.
- **`FeeType` hanya berisi kategori fee generik** (`PLATFORM_PAYIN`,
  `PLATFORM_WITHDRAWAL`, `GATEWAY_PROCESSING`, `PAYOUT`). Pembeda produk (donasi vs
  konten) ada di kolom **`product_type`** (kode produk consumer), **bukan** di enum.
  Jangan tambahkan `PLATFORM_DONATION`/`PLATFORM_CONTENT` ke enum.
- **`EvidenceSource` kini punya `SYSTEM`** — dipakai sebagai penanda batch settlement otomatis
  (bukan bukti eksternal). Nilai `API`/`REPORT_FILE`/`BANK_STATEMENT`/`MANUAL` tetap untuk
  jalur produksi/berbasis bukti.
- **`JournalReferenceType` belum punya `CHARGEBACK`.** Jurnal chargeback (J-11) saat ini
  harus diposting sebagai `REFUND` atau `ADJUSTMENT`.
- **Jangan posting J-2/J-3 tanpa J-1.** Settlement hanya untuk payment yang sudah `PAID`
  (kalau tidak, `CREATOR_PAYABLE_PENDING` bisa bersaldo negatif).
- **Grouping settlement**: `settlements.settlement_target` satu nilai per batch, jadi
  wajib kelompokkan per `provider_id` + `settlement_target`, jangan cuma per tanggal.
- **Fee PG pass-through.** Charge Midtrans pakai `gross_amount = gross + pg_fee`;
  `expected_settlement_amount = gross`. Jangan pernah membukukan fee PG ke
  `PG_FEE_EXPENSE` selama model ini.
- **J-3 per creator.** Jumlah creator bisa besar → key idempoten per creator
  (`...:RELEASE:{userId}`) dan pertimbangkan chunking.
- **Webhook `permitAll` wajib verifikasi signature.**
- **Tidak ada `@Scheduled`** — hanya Quartz.
- **Order ID** = `payment_attempts.id` (UUID v7) → matching webhook/report 1:1.
- Hari libur nasional di-maintain di `payment.holidays` tiap tahun (SKB).
- **Audit**: ledger append-only; tiap batch menyimpan `settlements` + `raw_evidence`.
  Mode portofolio (`variance = 0`) **bukan** pengganti rekonsiliasi produksi.
