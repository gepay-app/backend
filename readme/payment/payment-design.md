# Payment Module — Design Specification

> Modul `payment` menangani seluruh lifecycle pembayaran: **payin** (Midtrans/VA), **settlement** (reconciliation), **payout** (Flip/transfer), **refund**, dan **adjustment**.  
> Modul ini **hanya** bertanggung jawab atas logika bisnis pembayaran — **tidak pernah menulis jurnal langsung**. Semua pencatatan finansial didelegasikan ke `LedgerApi`.

---

## 1. Batas Modul & Prinsip

| Prinsip | Penjelasan |
|---------|------------|
| **Ledger-blind payment** | Payment tidak tahu struktur akun, hanya memanggil `LedgerApi.postJournal(...)` dengan `JournalLine` yang sudah disusun |
| **Single source of truth** | Semua angka keuangan (fee, tax, net) dihitung di payment, tapi **dicatat** di ledger |
| **Idempotency dari caller** | Payment generate `idempotencyKey` format `{TYPE}:{ID}:{ACTION}` dan passing ke ledger |
| **Evidence-based settlement** | Settlement hanya diposting kalau ada **bukti** (laporan PG / bank statement), bukan cuma timer T+n |

---

## 2. Entitas Utama (Payment Schema)

| Tabel | Fungsi |
|-------|--------|
| `providers` | Payment Gateway (Midtrans) & Payout Provider (Flip) |
| `channels` | Channel pembayaran (VA BCA, QRIS, dll) |
| `channel_routes` | Mapping provider ↔ channel + fee config |
| `fee_configs` | Rate card fee (versi, berlaku sejak kapan, komponen: platform, pg, tax) |
| `payments` | Transaksi donasi/pembayaran (status: PENDING, PAID, EXPIRED, FAILED, REFUNDED) |
| `payment_attempts` | Percobaan bayar per payment (1 payment bisa multi attempt) |
| `processed_events` | Idempotency key webhook (mencegah double-process) |
| `settlements` | Settlement dari PG ke bank platform (status: PENDING, CONFIRMED, FAILED) |
| `settlement_items` | Detail payment mana aja yang masuk settlement ini |
| `refunds` | Refund (status: PENDING, PROCESSING, COMPLETED, FAILED) |
| `withdrawals` | Permintaan tarik dana creator (status: PENDING, HOLD, PROCESSING, COMPLETED, FAILED) |
| `payout_destinations` | Rekening tujuan creator (bank account / e-wallet) |
| `payouts` | Eksekusi payout ke rekening creator (via Flip) |
| `fund_transfers` | Top-up lintas provider (Midtrans → Flip via bank) |
| `adjustments` | Koreksi manual (maker-checker, dual approval) |

---

## 3. Alur Utama (Happy Path)

### 3.1 Payin (Donasi Masuk)
```
Donatur → Midtrans (VA/QRIS) → Webhook payment.paid
    │
    ├─► PaymentService.onPaymentPaid()
    │     ├─ Validasi idempotency (processed_events)
    │     ├─ Hitung fee & tax (rate card snapshot)
    │     ├─ Update payment status = PAID
    │     └─ LedgerApi.postJournal(PAYMENT:PAY-1:PAID, lines...)
    │           DEBIT   PG_CLEARING_RECEIVABLE (1100)  100.000
    │           CREDIT  CREATOR_PAYABLE_PENDING (2100)  93.340
    │           CREDIT  PLATFORM_FEE_REVENUE (4000)      6.000
    │           CREDIT  VAT_PAYABLE (2300)                 660
    │
    └─► Settlement polling/job nanti akan handle
```

### 3.2 Settlement (Dana PG → Bank Platform)
```
Midtrans kirim laporan settlement (atau API)
    │
    ├─► SettlementService.confirmSettlement()
    │     ├─ Validasi evidence (file/JSON laporan)
    │     ├─ Match payment_attempts → settlement_items
    │     ├─ Update settlement status = CONFIRMED
    │     └─ LedgerApi.postJournal(SETTLEMENT:SET-1:CONFIRMED, lines...)
    │           DEBIT   PAYIN_PROVIDER_BALANCE (1150)  100.000
    │           CREDIT  PG_CLEARING_RECEIVABLE (1100)   100.000
    │
    └─► Release creator funds (bisa combine atau separate job)
          LedgerApi.postJournal(SETTLEMENT:SET-1:RELEASE, lines...)
          DEBIT   CREATOR_PAYABLE_PENDING (2100)   93.340
          CREDIT  CREATOR_PAYABLE_AVAILABLE (2110)  93.340
```

### 3.3 Withdrawal (Creator Tarik Dana)
```
Creator request withdraw → WithdrawalService.create()
    │
    ├─ Validasi saldo available (LedgerApi.getBalanceAmount)
    ├─ Create withdrawal status = PENDING
    ├─ LedgerApi.postJournal(WITHDRAWAL:WD-1:HOLD, lines...)
    │     DEBIT   CREATOR_PAYABLE_AVAILABLE (2110)  500.000
    │     CREDIT  WITHDRAWAL_PAYABLE (2200)         500.000
    │
    └─ PayoutService.processPayout() (async via Quartz)
          ├─ Call Flip API disburse
          ├─ LedgerApi.postJournal(PAYOUT:PO-1:COMPLETED, lines...)
          │     DEBIT   WITHDRAWAL_PAYABLE (2200)         500.000
          │     DEBIT   PAYOUT_FEE_EXPENSE (5100)           3.000
          │     CREDIT  WITHDRAWAL_FEE_REVENUE (4100)       2.500
          │     CREDIT  PAYOUT_PROVIDER_FLOAT (1300)      500.500
          │
          └─ Update withdrawal status = COMPLETED
```

### 3.4 Fund Transfer (Top-up Midtrans → Flip)
```
Ops team trigger top-up
    │
    ├─ FundTransferService.execute()
    ├─ LedgerApi.postJournal(FUND_TRANSFER:FT-1, lines...)
    │     DEBIT   PAYOUT_PROVIDER_FLOAT (1300)  503.000
    │     CREDIT  PAYIN_PROVIDER_BALANCE (1150) 503.000
    │
    └─ Actual bank transfer (manual/automated) → update status
```

### 3.5 Refund (Setelah Settlement)
```
Refund request (admin/auto)
    │
    ├─ RefundService.process()
    ├─ Validasi: payment PAID, settlement CONFIRMED, creator still has available balance
    ├─ LedgerApi.postJournal(REFUND:RF-1, lines..., reversesJournalId=J-1)
    │     DEBIT   CREATOR_PAYABLE_AVAILABLE (2110)  93.340
    │     DEBIT   PLATFORM_FEE_REVENUE (4000)         6.000
    │     DEBIT   VAT_PAYABLE (2300)                    660
    │     CREDIT  PAYIN_PROVIDER_BALANCE (1150)       100.000
    │
    └─ Midtrans refund API → update refund status
```

---

## 4. Fee & Tax Rate Card (Versi & Snapshot)

Fee **bukan** hardcoded. Disimpan di `fee_configs` dengan versi:

| Komponen | Tipe | Contoh |
|----------|------|--------|
| `platform_fee_bps` | Basis points | 600 (6%) |
| `pg_fee_bps` | Basis points | 290 (2.9%) |
| `vat_rate_bps` | Basis points | 110 (11%) |
| `payout_fee_flat` | Flat IDR | 2500 |
| `payout_fee_bps` | Basis points | 50 (0.5%) |

**Snapshot di transaksi**: Saat payment PAID, fee_config versi aktif di-copy ke `payment.fee_snapshot` (JSON) supaya audit trail "kenapa angkanya begini" tetap utuh meski rate card berubah nanti.

---

## 5. Idempotency Key Format (Payment → Ledger)

| Event | Format | Contoh |
|-------|--------|--------|
| Payment Paid | `PAYMENT:{paymentId}:PAID` | `PAYMENT:PAY-1:PAID` |
| Settlement Confirmed | `SETTLEMENT:{settlementId}:CONFIRMED` | `SETTLEMENT:SET-1:CONFIRMED` |
| Settlement Release | `SETTLEMENT:{settlementId}:RELEASE` | `SETTLEMENT:SET-1:RELEASE` |
| Withdrawal Hold | `WITHDRAWAL:{withdrawalId}:HOLD` | `WITHDRAWAL:WD-1:HOLD` |
| Payout Completed | `PAYOUT:{payoutId}:COMPLETED` | `PAYOUT:PO-1:COMPLETED` |
| Payout Failed | `PAYOUT:{payoutId}:FAILED` | `PAYOUT:PO-1:FAILED` |
| Fund Transfer | `FUND_TRANSFER:{fundTransferId}` | `FUND_TRANSFER:FT-1` |
| Refund | `REFUND:{refundId}` | `REFUND:RF-1` |
| Adjustment | `ADJUSTMENT:{adjustmentId}` | `ADJUSTMENT:ADJ-1` |

---

## 6. Module Configuration

- **Package**: `com.gepe.gepay.payment` (CLOSED module, `id="payment"`)
- **Allowed Dependencies**: `platform::API`, `ledger::API`
- **API Package**: `com.gepe.gepay.payment.api` (`@NamedInterface("api")`)
- **i18n**: `src/main/resources/i18n/payment/messages.properties` + `messages_id.properties`
- **Migration**: `V5__payment_tables.sql` (all payment tables)
