# TODO — GePay (Saweria/Trakteer-like)

Urutan wajib: `payment` → `donation` → `overlay` → `kyc`

## 1. `payment` (paling kritis, kerjakan paling teliti)
- [ ] Entity `Wallet` (balance = hasil hitung dari ledger, jangan update manual)
- [ ] Entity `LedgerEntry` (append-only, debit/kredit, tidak pernah di-update/delete)
- [ ] Entity `Transaction` (state: `PENDING → SUCCESS/FAILED/EXPIRED`)
- [ ] Unique constraint `gateway_reference_id` → cegah double-credit saat webhook retry (idempotency)
- [ ] Integrasi payment gateway (Midtrans/Xendit) — create transaction + handle webhook
- [ ] Webhook handler: update status SUCCESS + insert LedgerEntry + publish event `TransactionSucceeded` — semua dalam **satu DB transaction**
- [ ] Flyway migration schema `payment`
- [ ] Test: webhook dobel tidak boleh double-credit

## 2. `donation` (depends on `payment::API`)
- [ ] Entity `Donation` (pesan, nominal, nama donatur, `transactionId`, `status`: `PENDING → PAID → DISPLAYED`)
- [ ] Listener `on(TransactionSucceeded)` → mark `PAID` → publish `DonationPaid`
- [ ] Endpoint create donation (sebelum bayar)
- [ ] Flyway migration schema `donation`

## 3. `overlay` (depends on `donation` event)
- [ ] Listener `on(DonationPaid)` → push job ke Redis Stream
- [ ] Consumer baca stream → kirim ke FE via WebSocket
- [ ] Update status: `SENT → PLAYED` (ack dari FE)
- [ ] Retry exponential backoff (1s,2s,4s,8s,16s) max 5x → kalau tetap gagal → `FAILED`
- [ ] Endpoint/health untuk monitor pesan yang `FAILED` (buat replay manual)

## 4. `kyc` (bisa nyusul, wajib sebelum fitur withdraw)
- [ ] Field `kycStatus` di `identity` (`UNVERIFIED/VERIFIED`)
- [ ] Modul `kyc`: entity submission, upload dokumen, integrasi vendor verifikasi
- [ ] Event `KycApproved` → `identity` listener update `kycStatus`
- [ ] Guard: fitur withdraw cek `kycStatus == VERIFIED`

## Cross-cutting (jangan lupa)
- [ ] Semua modul ikut pola `api`/`internal` split + `package-info.java` CLOSED
- [ ] i18n bundle per modul (en + id)
- [ ] `ModularityTests` tetap hijau tiap nambah modul