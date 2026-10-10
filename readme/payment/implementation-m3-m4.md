# Payment — Catatan Implementasi Milestone 3 & 4

> Dokumen ini menjelaskan **apa yang dikerjakan** pada Milestone 3 (settlement otomatis)
> dan Milestone 4 (withdrawal & payout), supaya mudah ditinjau. Fokus: alur, jurnal,
> idempotency, dan **titik yang perlu diverifikasi manual**.
>
> Konsep: [overview.md](./overview.md) · Settlement: [settlement.md](./settlement.md) ·
> Contoh angka: [example.md](./example.md) · Rencana: [todo.md](./todo.md) ·
> Konvensi: [`../../AGENTS.md`](../../AGENTS.md).

---

## 0. Ringkasan

| Milestone | Hasil | Status |
|---|---|---|
| **M3 — Settlement** | Job Quartz harian men-settle payment `PAID` yang lewat T+n → batch `settlements` `CONFIRMED` → J-2 + J-3 | ✅ |
| **M4 — Withdrawal/Payout** | CRUD rekening tujuan + tarik dana (J-5) + job payout via Flip (J-6/J-7) | ✅ |
| M5 | refund/chargeback/reconciliation/adjustment/OpenAPI | ⬜ ditunda |

**Prinsip yang dijaga:** uang `Long` (rupiah bulat), jurnal selalu seimbang & hanya lewat
`LedgerApi.postJournal`, `@Transactional` hanya di service/writer, tanpa `@Scheduled`
(Quartz clustered), idempotency lewat key di DB (`journals.idempotency_key`,
`processed_events`, `idempotency_key` payment/withdrawal).

---

## 1. Peta File

### 1.1 File baru — Milestone 3 (Settlement)

| File | Peran |
|---|---|
| `internal/service/SettlementService.java` | Orkestrator: pilih kandidat + kelompokkan per `(provider_id, settlement_target)`. **Tanpa** `@Transactional`. |
| `internal/service/SettlementWriter.java` | Batas transaksi **per grup**: buat header `settlements`, posting J-2 & J-3, tandai payment `settled`. |
| `internal/job/SettlementJob.java` | `QuartzJobBean` `@DisallowConcurrentExecution`; ambil tanggal hari ini (Asia/Jakarta) lalu panggil service. |
| `internal/config/SettlementScheduler.java` | Daftarkan `JobDetail` + `Trigger` cron ke scheduler Quartz clustered. |
| `src/test/.../SettlementServiceTest.java` | Uji pengelompokan grup. |
| `src/test/.../SettlementWriterTest.java` | Uji batch `CONFIRMED`, J-2/J-3 seimbang, agregasi per creator, skip yang sudah settled. |

### 1.2 File baru — Milestone 4 (Withdrawal/Payout)

| File | Peran |
|---|---|
| `internal/provider/flip/FlipProperties.java` | Bind `pg.flip.*` (`secretKey`, `validationKey`, `url`). |
| `internal/provider/flip/FlipConfig.java` | `RestClient` Flip (Basic auth pakai `secretKey`). |
| `internal/provider/flip/FlipPayoutClient.java` | `implements PayoutProvider`: `disburse`, `getStatus`, `parseWebhook`. |
| `api/WithdrawalApi.java` | Facade publik: CRUD rekening tujuan + create/get withdrawal. |
| `api/dtos/PayoutDestinationCreateCommand.java` | Command buat rekening tujuan. |
| `api/dtos/PayoutDestinationResponse.java` | Response rekening tujuan. |
| `api/dtos/WithdrawalCreateCommand.java` | Command tarik dana. |
| `api/dtos/WithdrawalResponse.java` | Response penarikan. |
| `internal/service/WithdrawalService.java` | `implements WithdrawalApi`: ownership via `CurrentUser`, cek saldo, hold **J-5**. |
| `internal/service/PayoutService.java` | Orkestrator payout: pilih withdrawal, resolve fee, panggil provider. **Tanpa** `@Transactional`. |
| `internal/service/PayoutWriter.java` | Batas transaksi: `beginPayout` (buat/ulang payout) + `applyDisburseResult` (J-6/J-7). |
| `internal/job/PayoutJob.java` | `QuartzJobBean` `@DisallowConcurrentExecution`. |
| `internal/config/PayoutScheduler.java` | Trigger cron payout. |
| `internal/delivery/http/PayoutDestinationController.java` | Endpoint `/api/v1/payout-destinations`. |
| `internal/delivery/http/WithdrawalController.java` | Endpoint `/api/v1/withdrawals`. |
| `internal/delivery/http/req/CreatePayoutDestinationRequest.java` | Body request + validasi. |
| `internal/delivery/http/req/CreateWithdrawalRequest.java` | Body request + validasi. |
| `src/test/.../WithdrawalServiceTest.java` | Uji saldo, idempotensi, J-5 seimbang, ownership. |
| `src/test/.../PayoutWriterTest.java` | Uji J-6/J-7 seimbang, transisi status, reuse payout. |

### 1.3 File yang diubah (dan alasannya)

| File | Perubahan | Alasan |
|---|---|---|
| `payment/package-info.java` | `allowedDependencies` + `"identity::api"` | Ambil pemilik rekening dari `CurrentUser` (bukan payload). |
| `payment/api/enums/EvidenceSource.java` | Tambah `SYSTEM` | Penanda batch settlement otomatis (bukan bukti eksternal). |
| `payment/internal/entity/Settlement.java` | `recordEvidence(...)` | Simpan `raw_evidence` + `evidence_reference` (jejak audit batch otomatis). |
| `payment/internal/entity/Withdrawal.java` | `applyFeeSnapshot`, `markProcessing`, `markPaid`, `markFailed` | Snapshot fee + transisi status. |
| `payment/internal/entity/Payout.java` | `recordProviderReference`, `recordRawPayload`, `markCompleted`, `markFailed` | Simpan referensi vendor + payload, transisi status. |
| `payment/internal/entity/PayoutDestination.java` | `markDefault`, `clearDefault`, `deactivate` | Operasi CRUD rekening tujuan. |
| `payment/internal/repository/PaymentRepository.java` | `findSettlementCandidates` | Query kandidat settlement (`JOIN FETCH` route agar tidak N+1). |
| `payment/internal/repository/WithdrawalRepository.java` | `findByIdempotencyKey`, `findByUserId...`, `findByStatusIn...` | Idempotensi + kandidat payout. |
| `payment/internal/repository/PayoutDestinationRepository.java` | `findByUserId...`, `findByIdAndUserId` | Daftar & cek kepemilikan. |
| `payment/internal/repository/PayoutRepository.java` | `findByProviderReferenceId`, `findFirstByWithdrawalId...` | Lookup payout terbaru saat retry. |
| `payment/internal/exception/PaymentError.java` | 6 error baru | Lihat §6. |
| `i18n/payment/messages.properties` + `_id.properties` | Key baru (en + id lockstep) | Sesuai §6 AGENTS.md. |
| `application.yaml` | `pg.flip.*`, `payment.settlement.cron`, `payment.payout.cron` | Konfigurasi provider & jadwal. |
| `.env.example` | Placeholder `PG_FLIP_*` & `PG_MIDTRANS_*` | Template env (nilai asli tetap di `.env`, gitignored). |
| `readme/payment/todo.md` | Status M3 & M4 ✅ | Update tracker tiap milestone. |
| **dihapus**: `api/dtos/WithdrawalCreateRequest.java`, `api/dtos/PayoutDestinationCreateRequest.java` | Diganti Command DTO | Menghindari dead code (`userId` dari payload tidak dipakai). |

---

## 2. Alur Settlement (Milestone 3)

### 2.1 Kapan jalan
Job Quartz `SettlementJob` jalan **tiap hari 03:00 Asia/Jakarta** (cron
`payment.settlement.cron`, bisa dioverride env). Clustered → hanya 1 instance yang jalan.
Misfire = fire-and-proceed (kalau server sempat mati, run berikutnya mengejar / catch-up).

### 2.2 Urutan

```
SettlementJob.executeInternal()
  └─ SettlementService.settleDuePayments(today)          [tanpa tx]
       ├─ paymentRepository.findSettlementCandidates(PAID, today)
       │     syarat: status=PAID, settlement_id IS NULL, expected_settlement_date <= today
       ├─ grouping: (providerId, channelRoute.settlementTarget)
       └─ untuk tiap grup:
            SettlementWriter.settleGroup(providerId, target, paymentIds)   [@Transactional]
              ├─ findAllById → filter ulang (PAID & belum settled)          ← guard race
              ├─ Provider = providerRepository.findById(providerId)
              ├─ expectedAmount = Σ payment.expectedSettlementAmount
              ├─ Settlement.create(... SYSTEM ...) + recordEvidence(...)
              ├─ matchExpected(expected) + confirm(expected, now)  → CONFIRMED, variance=0
              ├─ saveAndFlush(settlement)
              ├─ post J-2  ("SETTLEMENT:{id}:CONFIRMED")
              ├─ post J-3 per creator ("SETTLEMENT:{id}:RELEASE:{userId}")
              └─ payment.markSettled(settlementId, now) untuk tiap payment → saveAll
```

### 2.3 Kenapa dua kelas (`Service` + `Writer`)
Panggilan `ledgerApi.postJournal` adalah cross-module call yang ikut transaksi pemanggil.
Dengan memisahkan writer ber-`@Transactional`, tiap **grup** punya transaksi sendiri:
kalau satu grup gagal, grup lain tetap lanjut dan yang gagal bisa dicoba lagi run
berikutnya (payment-nya belum ditandai `settled`).

### 2.4 Idempotency
- Payment yang sudah punya `settlement_id` **tidak** ikut kandidat lagi.
- Jurnal pakai key unik `SETTLEMENT:{settlementId}:CONFIRMED` / `...:RELEASE:{userId}`.
  Karena `settlementId` baru tiap batch, key ini mencegah **dobel posting** jika writer
  dipanggil dua kali untuk batch yang sama.
- `markSettled` dan posting jurnal berada di **transaksi yang sama** → konsisten
  (commit bareng atau rollback bareng).

### 2.5 Akun & angka (contoh `example.md`)
Batch `SET-1`, `target=BANK`, provider `MIDTRANS`, Σ expected = 100.000:

**J-2** `SETTLEMENT:SET-1:CONFIRMED`

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `BANK_OPERATING` | `BANK-1` | Debit | 100.000 |
| `PG_CLEARING_RECEIVABLE` | `MIDTRANS` | Kredit | 100.000 |

> Jika `settlement_target = PROVIDER_BALANCE`, debit-nya `PAYIN_PROVIDER_BALANCE`
> (owner = kode provider), bukan `BANK_OPERATING`.

**J-3** `SETTLEMENT:SET-1:RELEASE:USER-123` (dibuat **per creator**, net diagregasi)

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_PENDING` | `USER-123` | Debit | 93.340 |
| `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | Kredit | 93.340 |

Rincian kode: `SettlementWriter.postJ2` / `postJ3`.

---

## 3. Alur Withdrawal & Payout (Milestone 4)

### 3.1 Buat rekening tujuan (payout destination)
`POST /api/v1/payout-destinations` → `WithdrawalApi.createPayoutDestination` →
`WithdrawalService`:
1. `channelId` harus channel aktif ber-direction `PAYOUT`, kalau tidak → `CHANNEL_NOT_PAYOUT`.
2. Simpan `PayoutDestination` dengan `user_id = CurrentUser.userId()`.

Endpoint lain: `GET` (daftar), `POST /{id}/default`, `DELETE /{id}` (nonaktifkan).
Semua operasi memverifikasi kepemilikan (`PAYOUT_DESTINATION_NOT_OWNED`).

### 3.2 Tarik dana (create withdrawal)
`POST /api/v1/withdrawals` → `WithdrawalService.createWithdrawal` (`@Transactional`):

```
1. idempotency: findByidempotency_key → kalau ada, replay (return existing)
2. ownership: payoutDestinationRepository.findById(id) ; cek user_id == CurrentUser
3. route = findActiveRoute(destination.channelId)   (dapat provider + provider_channel_code)
4. validasi min/max amount terhadap route
5. fee = PLATFORM_WITHDRAWAL config (flat; VAT diabaikan sesuai contoh)
6. net = requested - fee
7. available = ledgerApi.getOrCreateAccount(CREATOR_PAYABLE_AVAILABLE, userId).balance()
   kalau available < requested → INSUFFICIENT_AVAILABLE_BALANCE
8. simpan Withdrawal + applyFeeSnapshot(fee, net)
9. post J-5  ("WITHDRAWAL:{id}:HOLD")
10. (race) DataIntegrityViolation → lookup by idempotency_key → replay
```

> Catatan: `requestedAmount` = nominal bruto yang diminta (termasuk fee).
> Contoh `example.md`: 90.000 → fee 3.000 → net 87.000.

**J-5** `WITHDRAWAL:WD-1:HOLD`

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | Debit | 90.000 |
| `WITHDRAWAL_PAYABLE` | `USER-123` | Kredit | 90.000 |

### 3.3 Proses payout (job)
`PayoutJob` → `PayoutService.processPayouts()` (cron `payment.payout.cron`, default 5 menit):

```
withdrawals = findByStatusIn([REQUESTED, PROCESSING])
untuk tiap withdrawal (dibungkus try/catch agar 1 gagal tidak menghentikan semua):
  route   = channelRouteRepository.findById(routeId)      → providerId, channelId, providerChannelCode
  provider= providerRepository.findById(...) aktif & supports_payout
  adapter = PayoutProvider yang code-nya == provider.code (FlipPayoutClient → "FLIP")
  kalau status == REQUESTED: providerFee = PAYOUT config(providerId, channelId)
  payoutId = PayoutWriter.beginPayout(withdrawalId, providerId, net, providerFee)   [@Transactional]
  result  = adapter.disburse(DisbursementRequest)            ← REMOTE I/O di luar transaksi
  PayoutWriter.applyDisburseResult(payoutId, providerCode, result)                   [@Transactional]
```

`PayoutWriter.beginPayout`:
- withdrawal `REQUESTED` → `markProcessing()` + buat `Payout(PENDING)` baru, kembalikan `payoutId`.
- withdrawal `PROCESSING` → pakai **payout terbaru** (untuk polling/retry), tidak buat baru.

`PayoutWriter.applyDisburseResult`:
- `COMPLETED` → `payout.markCompleted` + `withdrawal.markPaid` + **J-6**.
- `FAILED`/`REVERSED` → `payout.markFailed` + `withdrawal.markFailed` + **J-7**.
- `PENDING` → simpan `provider_reference_id` + `raw_payload`; withdrawal tetap `PROCESSING`
  (dicoba lagi run berikutnya — disburse idempoten lewat header `idempotency-key`).

**J-6** (`PAYOUT:{id}:COMPLETED`) — contoh sukses: requested 90.000, fee WD 3.000,
net 87.000, fee Flip 2.500

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `WITHDRAWAL_PAYABLE` | `USER-123` | Debit | 90.000 |
| `PAYOUT_FEE_EXPENSE` | – | Debit | 2.500 |
| `WITHDRAWAL_FEE_REVENUE` | – | Kredit | 3.000 |
| `PAYOUT_PROVIDER_FLOAT` | `FLIP` | Kredit | 89.500 |

**J-7** (`PAYOUT:{id}:FAILED`) — hold dikembalikan

| Akun | Owner | Arah | Nominal |
|---|---|---|---|
| `WITHDRAWAL_PAYABLE` | `USER-123` | Debit | 90.000 |
| `CREATOR_PAYABLE_AVAILABLE` | `USER-123` | Kredit | 90.000 |

### 3.4 ID transaksi
- `payouts.id` (UUID v7) dipakai sebagai `idempotency-key` header ke Flip.
- `provider_reference_id` = `id` disbursement dari Flip (referensi vendor).

---

## 4. Endpoint Baru

| Method | Path | Body | Facade |
|---|---|---|---|
| POST | `/api/v1/payout-destinations` | `{channelId, accountNumber, accountName, bankCode}` | `WithdrawalApi.createPayoutDestination` |
| GET | `/api/v1/payout-destinations` | – | `WithdrawalApi.listPayoutDestinations` |
| POST | `/api/v1/payout-destinations/{id}/default` | – | `WithdrawalApi.setDefaultPayoutDestination` |
| DELETE | `/api/v1/payout-destinations/{id}` | – | `WithdrawalApi.deactivatePayoutDestination` |
| POST | `/api/v1/withdrawals` | `{idempotencyKey, destinationId, requestedAmount}` | `WithdrawalApi.createWithdrawal` |
| GET | `/api/v1/withdrawals?cursor=&size=` | – | `WithdrawalApi.listWithdrawals` |
| GET | `/api/v1/withdrawals/config?destinationId=` | – | `WithdrawalApi.getWithdrawalConfig` |
| GET | `/api/v1/withdrawals/{id}` | – | `WithdrawalApi.getWithdrawal` |
| GET | `/api/v1/channels?direction=PAYIN|PAYOUT` | – | `PaymentApi.listChannels` (publik) |
| GET | `/api/v1/payments?cursor=&size=` | – | `PaymentApi.listPayments` (earnings, cursor-paginated) |

> `GET /api/v1/channels` **publik** (tanpa auth): donor memilih metode bayar
> (`PAYIN`), creator memilih tujuan pencairan (`PAYOUT`).
> `GET /api/v1/balance` **bukan** milik payment: saldo adalah data `ledger`, jadi
> endpoint + DTO-nya tinggal di modul `ledger` (lihat
> [`readme/ledger/overview.md`](../ledger/overview.md)). `WithdrawalApi` sengaja
> tidak lagi mengekspos saldo/channel — facade tetap satu concern.
> List memakai `CursorPage<T>` (`items/hasNext/nextCursor`) — keyset by id (`?cursor=<nextCursor>`).

Semua respons memakai envelope `ApiResponse<T>`; pesan sukses dari `MessageHelper`
(key i18n, bukan string hardcode).

---

## 5. Keputusan Desain Penting

1. **Kepemilikan dari `CurrentUser`**, bukan dari payload. `userId` dihapus dari request
   DTO withdrawal/destination. Ini menutup celah user membuat penarikan atas nama orang lain.
2. **Pemisahan orkestrator (`@Transactional` = tidak) + writer (`@Transactional`)** untuk
   settlement & payout. Tujuannya: remote I/O (Flip) dan `LedgerApi` call berada di luar
   transaksi; konsisten dengan pola `PaymentService`/`PaymentWriter` yang sudah ada.
3. **`EvidenceSource.SYSTEM`** ditambahkan sebagai penanda batch otomatis (bukan bukti).
   Ini perubahan pada enum `api` (aditif, tidak breaking).
4. **`identity::api`** ditambahkan ke `allowedDependencies` payment — satu-satunya
   ketergantungan baru payment di milestone ini.
5. **Grouping settlement by `provider_id + settlement_target`** (bukan hanya tanggal),
   karena `settlements.settlement_target` hanya satu nilai per baris.
6. **J-3 per creator** dengan agregasi net (`groupingBy(userId, summingLong(net))`),
   idempotency key per creator.
7. **Flip disburse** dikirim `application/x-www-form-urlencoded` (kontrak API Flip),
   bukan JSON, walau contoh payload di brief ditulis JSON.

---

## 6. Error Code Baru

| Enum | HTTP | Key | Dipakai saat |
|---|---|---|---|
| `INVALID_FLIP_WEBHOOK` | 400 | `payment.invalid_flip_webhook` | Signature webhook Flip tidak valid. |
| `CHANNEL_NOT_PAYOUT` | 400 | `payment.channel_not_payout` | Channel rekening tujuan bukan channel payout. |
| `PAYOUT_DESTINATION_NOT_FOUND` | 404 | `payment.payout_destination_not_found` | Rekening tidak ada / nonaktif. |
| `PAYOUT_DESTINATION_NOT_OWNED` | 403 | `payment.payout_destination_not_owned` | Rekening milik user lain. |
| `INSUFFICIENT_AVAILABLE_BALANCE` | 400 | `payment.insufficient_available_balance` | Saldo available < requested. |
| `WITHDRAWAL_NOT_FOUND` | 404 | `payment.withdrawal_not_found` | Penarikan tidak ada. |
| `PAYOUT_NOT_FOUND` | 404 | `payment.payout_not_found` | Payout tidak ada. |

Semua di `PaymentError` (bukan `GlobalError`) + key en/id di i18n `payment/`.

---

## 7. Yang BELUM Dikerjakan / Batasan (sadar)

- **Milestone 5 ditunda**: refund, chargeback, adjustment (J-10), admin fee config,
  reconciliation, OpenAPI.
- **Bank-account-inquiry Flip tidak dipakai.** Flip menyediakan
  `POST /v2/disbursement/bank-account-inquiry`, tapi tidak saya sambungkan ke alur
  (butuh keputusan: validasi saat buat rekening? saat payout?). Bisa ditambah menyusul.
- **Webhook payout belum dirutekan.** `WebhookController` saat ini hanya mencari
  `PayinProvider`. `FlipPayoutClient.parseWebhook` sudah ada (verifikasi header `token`
  = `validationKey`), tapi belum ada route `/api/v1/webhooks/flip` yang memanggilnya.
  Untuk sekarang status payout diambil **sinkron** dari respons disburse + polling via
  re-disburse idempoten di job.
- **`getStatus` Flip** mengembalikan `ProviderStatus` (enum `PaymentAttemptStatus`) karena
  itu kontrak SPI `PayoutProvider` yang ada; belum dipakai di alur payout.
- **Fee `PLATFORM_WITHDRAWAL` tanpa VAT** (sesuai `example.md`; seed `vat_bps=0`).

---

## 8. Titik yang Perlu Kamu Cek Manual (asumsi / risiko)

1. **Bentuk request/response Flip.** Dokumentasi Flip 404 saat ditelusuri, jadi
   pemetaan `status` (`DONE`/`PENDING`/`FAILED`/`REVERSED`/`CANCELLED`) dan nama field
   (`id`, `status`, `fee`) berdasarkan kontrak yang umum diketahui. **Verifikasi di
   sandbox** sebelum dianggap final. Kode ada di `FlipPayoutClient.mapStatus`.
2. **Header webhook Flip.** Saya asumsikan token di header bernama `token`. Kalau Flip
   memakai nama header lain, `parseWebhook` perlu disesuaikan (walau saat ini belum dirutekan).
3. **`getOrCreateAccount` saat cek saldo.** Membuat akun `CREATOR_PAYABLE_AVAILABLE`
   jika belum ada (saldo 0). Ini disengaja (akun per-owner dibuat lazy), tapi pastikan
   perilaku ini memang diinginkan.
4. **Race settlement.** Aman selama Quartz clustered + `@DisallowConcurrentExecution`
   menjamin satu instance. Guard ganda ada di `SettlementWriter` (filter ulang status).
5. **Retry payout setelah `FAILED`.** Saat ini withdrawal `FAILED` **tidak** dicoba lagi
   (creator harus buat withdrawal baru). `REVERSED` diperlakukan sama seperti `FAILED`.
   Kalau mau retry otomatis, alurnya perlu diubah.
6. **Provider fee payout hanya dihitung saat `REQUESTED`.** Saat `PROCESSING` (retry),
   `beginPayout` memakai payout lama apa adanya. Pastikan ini sesuai ekspektasi.
7. **Konfigurasi Flip** harus ada di env (`PG_FLIP_SECRET_KEY`, `PG_FLIP_VALIDATION_KEY`,
   `PG_FLIP_URL`). Tanpa itu, aplikasi gagal start (`@NotBlank` + `@Validated`).

---

## 9. Konfigurasi & Cara Coba

**Env baru** (`.env`, sudah ada nilainya; `.env.example` sudah diberi placeholder):

```
PG_FLIP_SECRET_KEY=...
PG_FLIP_VALIDATION_KEY=...
PG_FLIP_URL=https://bigflip.id/big_sandbox_api
```

**Jadwal** (`application.yaml`, override via env):

```
payment.settlement.cron = ${PAYMENT_SETTLEMENT_CRON:0 0 3 * * ?}   # 03:00 Asia/Jakarta
payment.payout.cron     = ${PAYMENT_PAYOUT_CRON:0 0/5 * * * ?}     # tiap 5 menit
```

**Menjalankan**: `./mvnw spring-boot:run` (butuh Postgres + Redis).

---

## 10. Testing

```bash
./mvnw test                                   # semua (termasuk ModularityTests)
./mvnw test -Dtest='SettlementWriterTest,WithdrawalServiceTest,PayoutWriterTest'
```

Hasil saat ini: seluruh test hijau, `ModularityTests` (batas modul) & `ApplicationTests`
(context) lolos. `./mvnw verify -DskipTests` juga sukses.

Test yang ditambahkan fokus pada **perilaku**:
- J-2/J-3/J-5/J-6/J-7 **seimbang** (Σdebit = Σkredit).
- Idempotensi (event/payment/withdrawal dobel tidak memposting dua kali).
- Seleksi & agregasi kandidat settlement per creator.
- Ownership + cek saldo withdrawal.
