# Payment Module — Design Specification

> Modul `payment` menangani seluruh lifecycle pembayaran: **payin** (Midtrans/VA/QRIS), **settlement** (pencairan dana PG ke platform, berbasis bukti), **payout** (Flip), **withdrawal**, **refund**, dan **adjustment**.
> Modul ini **tidak pernah menulis jurnal langsung** — semua pencatatan finansial didelegasikan ke `LedgerApi` (modul `ledger`).

---

## 1. Prinsip Inti

| Prinsip | Penjelasan |
|---|---|
| **Vendor-blind** | Modul ini **tidak** mengimpor tipe Midtrans/Flip. Bahasa vendor dipetakan ke kontrak internal oleh **adapter** per provider. Ganti/tambah PG = tambah adapter + seed 1 baris `providers`, tanpa ubah service/entity/controller. |
| **Ledger-blind payment** | Payment tidak tahu struktur akun; hanya memanggil `LedgerApi.postJournal(...)` dengan `JournalLine` yang sudah disusun. |
| **Idempotency dari caller** | Payment membentuk `idempotencyKey` `{TYPE}:{ID}:{ACTION}` dan meneruskannya ke ledger. |
| **Evidence-based settlement** | Settlement **hanya** diposting kalau ada bukti (mutasi bank / laporan PG / konfirmasi manual), bukan cuma timer T+n. |

---

## 2. Pemisahan 3 konsep yang sering tercampur

| # | Konsep | Arti | Sumber sinyal |
|---|---|---|---|
| A | **Status transaksi PG** | vocab vendor (`pending`, `settlement`, `capture`, `expire`, `deny`, `cancel`) | webhook/API vendor |
| B | **Paid** (customer sudah bayar) | uang diterima PG, PG berutang ke kita | webhook (status A dipetakan adapter) |
| C | **Settlement platform** | dana benar-benar pindah ke rekening/platform | **bukti** (mutasi bank/CSV/MAP), bukan webhook |

**Aturan**: transaksi cukup sampai status `PAID`. Konsep C hidup di agregat terpisah (`settlements` batch) yang diisi manusia/bukti. Midtrans memakai kata `settlement` untuk konsep **B** — jangan disamakan dengan konsep **C**.

---

## 3. Entitas Utama (Payment Schema, `V5__payment_tables.sql`)

| Tabel | Fungsi |
|---|---|
| `providers` | PG payin & payout provider (MIDTRANS, FLIP, ...) |
| `channels` | Channel logis (VA_BCA, QRIS, BANK_BCA, ...) |
| `channel_routes` | Mapping provider ↔ channel + **kebijakan settlement** (`settlement_delay_days`, `settlement_business_days`, `settlement_target`) |
| `holidays` | Hari libur nasional (untuk hitung hari kerja) |
| `fee_configs` | Rate card berversi (platform/gateway/payout) |
| `user_fee_overrides` | Override fee per user (VIP) |
| `payments` | Transaksi donasi/content (status: INITIATED, PENDING, PAID, EXPIRED, FAILED, CANCELLED, PARTIALLY_REFUNDED, REFUNDED) |
| `payment_attempts` | Percobaan bayar per payment (1 payment bisa multi attempt; `provider_reference_id` = `order_id` = UUID v7) |
| `settlements` | **Batch** pencairan dana PG → platform (status: PENDING, CONFIRMED, CANCELLED) |
| `refunds` | Refund |
| `withdrawals` | Permintaan tarik dana creator |
| `payout_destinations` | Rekening tujuan creator |
| `payouts` | Eksekusi payout ke rekening creator (via Flip) |
| `fund_transfers` | Top-up antar akun platform (bank → Flip, dsb) |
| `adjustments` | Koreksi manual (maker-checker) |
| `processed_events` | Inbox idempotency webhook |
| `reconciliation_runs` / `reconciliation_items` | Rekonsiliasi (opsional, fase lanjut) |

> Catatan penting:
> - `payments` menyimpan **snapshot** `provider_id`, `channel_id`, `channel_route_id`, dan seluruh komponen fee — supaya audit trail utuh meski rate card/routing berubah.
> - `payments.settlement_id` (FK ke `settlements`) menghubungkan transaksi ke batch settlement-nya; **tidak ada** tabel `settlement_items` — 1 payment selalu masuk 1 batch.

---

## 4. Adapter Provider (SPI)

```
internal/provider/
├── PaymentProviderClient.java      # SPI payin: createCharge, verifySignature, parseEvent
├── PayoutProviderClient.java       # SPI payout: disburse, verifySignature, parseEvent
├── ProviderEvent.java              # event webhook kanonik (vendor-blind)
├── ProviderClientRegistry.java     # provider.code -> client
├── midtrans/MidtransPayinClient.java
└── flip/FlipPayoutClient.java
```

Pemetaan status Midtrans → event kanonik:

| Midtrans `transaction_status` | Event kanonik |
|---|---|
| `settlement` (+`fraud_status=accept`) | `PAYMENT_PAID` |
| `pending` | `PAYMENT_PENDING` |
| `expire` | `PAYMENT_EXPIRED` |
| `deny` | `PAYMENT_FAILED` |
| `cancel` | `PAYMENT_CANCELLED` |
| `capture` (kartu) | `PAYMENT_PAID` |

---

## 5. Settlement — Batch Berbasis Bukti

Karena Midtrans **tidak** mengirim sinyal "dana sudah available/bisa ditarik" (pencairan manual dari MAP, transaksi baru boleh dicairkan ≥ 3 hari kerja setelah `settlement`), maka:

1. `payments.expected_settlement_date` = `paid_at` + `T+n` **hari kerja** (dari `channel_routes.settlement_delay_days` + `settlement_business_days`, memakai tabel `holidays`).
2. `expected_settlement_date` hanya **monitor** (job OVERDUE), bukan pemicu jurnal.
3. Bukti keanggotaan: CSV/report → match `order_id` (= `payment_attempts.provider_reference_id`).
4. Bukti nominal: mutasi bank / konfirmasi MAP → `actual_amount`.
5. `settlements` header menyimpan `expected_amount` (Σ yang di-match), `actual_amount`, `variance_amount`, dan `evidence_*`.
6. Konfirmasi batch → status `CONFIRMED` → posting jurnal.

Untuk PG yang **memang** punya webhook/report settlement, adapter cukup mengisi batch yang sama — alur internal identik.

---

## 6. Idempotency Key Format (Payment → Ledger)

| Event | Format | Contoh |
|---|---|---|
| Payment Paid | `PAYMENT:{paymentId}:PAID` | `PAYMENT:PAY-1:PAID` |
| Settlement Confirm | `SETTLEMENT:{settlementId}:CONFIRMED` | `SETTLEMENT:SET-1:CONFIRMED` |
| Settlement Release | `SETTLEMENT:{settlementId}:RELEASE` | `SETTLEMENT:SET-1:RELEASE` |
| Withdrawal Hold | `WITHDRAWAL:{withdrawalId}:HOLD` | `WITHDRAWAL:WD-1:HOLD` |
| Payout Completed | `PAYOUT:{payoutId}:COMPLETED` | `PAYOUT:PO-1:COMPLETED` |
| Payout Failed | `PAYOUT:{payoutId}:FAILED` | `PAYOUT:PO-1:FAILED` |
| Fund Transfer | `FUND_TRANSFER:{fundTransferId}` | `FUND_TRANSFER:FT-1` |
| Refund | `REFUND:{refundId}` | `REFUND:RF-1` |
| Adjustment | `ADJUSTMENT:{adjustmentId}` | `ADJUSTMENT:ADJ-1` |

---

## 7. Module Configuration

- **Package**: `com.gepe.gepay.payment` (CLOSED, `id="payment"`, `allowedDependencies = "ledger::API"`)
- **API Package**: `com.gepe.gepay.payment.api` (`@NamedInterface("api")`)
- **i18n**: `src/main/resources/i18n/payment/messages.properties` + `messages_id.properties`
- **Migration**: `V5__payment_tables.sql` (semua tabel payment)
- **Expiry**: `payments`/`payment_attempts` default **1 jam**, configurable
- **Business days**: `payment.holidays` + `BusinessDayCalculator` (Sabtu/Minggu + libur nasional)
- **Kredensial**: `PG_MIDTRANS_SERVER_KEY`, `PG_MIDTRANS_CLIENT_KEY`, `PG_MIDTRANS_MERCHANT_ID`, `PG_FLIP_SECRET_KEY`, `PG_FLIP_VALIDATION_KEY`

---

## 8. Alur Dana (Ringkasan)

```
Donatur → PG (VA/QRIS) → webhook PAID (J-1: receivable)
    → Settlement batch (bukti) → CONFIRMED
        J-2: PG_CLEARING_RECEIVABLE → BANK_OPERATING
        J-3: creator PENDING → AVAILABLE
    → Fund transfer bank → payout provider (top-up Flip)
    → Withdrawal HOLD (available → withdrawal payable)
    → Payout via Flip → COMPLETED (withdrawal payable cleared + fee)
```

Detail lengkap + jurnal per step: lihat [`payment-example.md`](./payment-example.md). Rencana kerja berurut: [`todo.md`](./todo.md).
