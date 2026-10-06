# Payment Module — Rencana Kerja (Berurutan)

> Kerjakan **urut dari atas ke bawah**. Setiap milestone punya hasil yang bisa kamu lihat/uji.
> Belum lanjut ke milestone berikutnya sebelum milestone sekarang hijau.
>
> Sumber desain: [`payment-design.md`](./payment-design.md) · Alur dana: [`payment-example.md`](./payment-example.md) · Status: [`states.md`](./states.md) · Settlement manual: [`manual-settlement.md`](./manual-settlement.md) · Konvensi: [`AGENTS.md`](../../AGENTS.md).

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

## Milestone 3 — Settlement (Batch Berbasis Bukti)

**Hasil**: admin input bukti → batch terkonfirmasi → saldo creator `PENDING → AVAILABLE`.

1. `SettlementRepository`, `SettlementService`.
2. Endpoint admin (SUPER_ADMIN): `POST /api/v1/settlements` — buat batch dari bukti (provider, `actual_amount`, evidence, periode). **Default**: auto-match by rule (payment `PAID`, provider cocok, belum ada `settlement_id`, `expected_settlement_date ≤ cutoff`); transaksi **tidak** dipilih manual. Opsi `orderIds` hanya fallback untuk withdraw sebagian.
3. `POST /api/v1/settlements/{id}/confirm` — set `CONFIRMED` + `actual_amount` + `variance`, posting **J-2** (dana ke bank) lalu **J-3** (release per creator).
4. Job Quartz `OverdueSettlement` (monitor: payment `PAID` lewat `expected_settlement_date` belum ter-settle → alert, **tanpa jurnal**).
5. Test: match & variance, confirm idempoten, saldo ledger benar.

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
- `order_id` Midtrans = `payment_attempts.id` (UUID v7), jadi CSV match 1:1.
- Hari libur nasional di-maintain di `payment.holidays` tiap tahun (SKB).
- **Settlement manual**: baca [`manual-settlement.md`](./manual-settlement.md) —
  CSV harian bukan penanda "withdrawable"; konfirmasi batch hanya dari bukti dana
  masuk. Basis nominal sudah final: **fee PG ditanggung donatur** (§7), jadi
  charge wajib memakai `gross_amount = gross + pg_fee`.
- **Ingest CSV** Midtrans (`Order ID`, `Amount`, `Total Fee`, `Settlement time`)
  perlu untuk verifikasi/`pg_fee_amount` dan deteksi order yang belum `PAID` —
  **bukan** untuk memilih order per transaksi.
- **Skala settlement**: pemilihan transaksi set-based (satu query/`UPDATE`), 1 batch
  per pencairan. J-3 diposting **per creator**; saat creator banyak, pakai key
  idempoten per creator (`…:RELEASE:{userId}`) dan chunk. Lihat
  [`manual-settlement.md` §5.8](./manual-settlement.md).
