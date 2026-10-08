# Payment — Contoh Alur Uang Lengkap

> **Baca ini kalau** kamu ingin melihat satu perjalanan uang dari donatur sampai
> creator, lengkap dengan status dan jurnal di tiap langkah.
>
> Konsep: [payment-design.md](./payment-design.md) · status:
> [states.md](./states.md) · istilah: [Glosarium](../glossary.md) · jurnal
> ledger: [ledger-example.md](../ledger/ledger-example.md).
>
> Alur: **duit masuk PG → settlement ke bank → hak creator PENDING→AVAILABLE →
> top-up payout provider → creator withdraw**. Semua nominal IDR bulat (`BIGINT`).

---

## Asumsi Contoh

- Donatur bayar donasi **Rp 100.000** via Midtrans VA BCA, creator `USER-123`.
- Platform fee: `PLATFORM_DONATION` = fixed 1.000 + 5% → **6.000**; PPN 11% → **660**. Net creator = 100.000 − 6.660 = **93.340**.
- PG fee VA BCA (ditanggung donatur): fixed 4.000 + PPN 11% (440) → **4.440**. Donatur membayar **104.440**; Midtrans mengambil 4.440; **platform menerima gross 100.000**. Fee PG tidak masuk ledger.
- `channel_routes` untuk VA BCA: `settlement_delay_days=3` (3 hari kerja), `settlement_target=BANK`.

ID disingkat: `PAY-1`, `ATT-1`, `SET-1`, `WD-1`, `PO-1`, `FT-1`. Jurnal: `J-1`..`J-6`.

---

## Step 0 — Create Donation (Payment + Attempt)

**Input**: `type=DONATION`, `gross=100.000`, `channel=VA_BCA` → route Midtrans.

**DB state**:
```sql
-- payment.payments (fee di-snapshot, status PENDING)
INSERT (id='PAY-1', provider_id=1 /*MIDTRANS*/, channel_id=2, channel_route_id=…,
        gross_amount=100000, platform_fee_amount=6660, pg_fee_amount=4440,
        total_charged_amount=104440, net_creator_amount=93340,
        expected_settlement_amount=100000, status='PENDING');

-- payment.payment_attempts (order_id = UUID v7 attempt itu sendiri)
INSERT (id='ATT-1', payment_id='PAY-1', channel_route_id=…,
        provider_reference_id='ATT-1', status='PENDING', expires_at=now()+1h);
```

Belum ada jurnal.

---

## Step 1 — Webhook PAID (Midtrans `transaction_status=settlement`)

Midtrans kirim webhook status `settlement` (+`fraud_status=accept`). Adapter memetakan ke event kanonik `PAYMENT_PAID`.

**DB state**:
```sql
-- payments.status='PAID', paid_at=…, expected_settlement_date = paid_at + 3 hari kerja
-- payment_attempts.status='PAID'
-- processed_events: (provider_id=1, external_event_id='<id webhook>', type='PAYMENT_PAID')
```

**Ledger Jurnal → J-1** (`PAYMENT:PAY-1:PAID`):

| Account | Owner | Direction | Amount |
|---|---|---|---|
| `PG_CLEARING_RECEIVABLE` (1100) | MIDTRANS | DEBIT | 100.000 |
| `CREATOR_PAYABLE_PENDING` (2100) | USER-123 | CREDIT | 93.340 |
| `PLATFORM_FEE_REVENUE` (4000) | – | CREDIT | 6.000 |
| `VAT_PAYABLE` (2300) | – | CREDIT | 660 |

Σ DEBIT = Σ CREDIT = 100.000 ✓. Uang sekarang "di Midtrans" (piutang), creator **masih PENDING**.

---

## Step 2 — Settlement (otomatis via job Quartz, T+n)

Mode portofolio: tidak ada rekening bank nyata. Job Quartz harian (03:00
Asia/Jakarta) melihat `expected_settlement_date` (`paid_at` + T+3 hari kerja =
`2026-10-06`) sudah terlewati, memilih payment `PAID` yang belum ter-settle, lalu
membuat + mengonfirmasi batch otomatis.

**DB state**:
```sql
-- payment.settlements (batch header, dibuat job)
INSERT (id='SET-1', provider_id=1, status='CONFIRMED', expected_amount=100000,
        actual_amount=100000, variance_amount=0, settlement_target='BANK',
        evidence_source='<penanda sistem>', actual_settled_at=…, confirmed_at=…);

-- payment.payments.settlement_id='SET-1', settled_at=…
```

**Ledger Jurnal → J-2** (`SETTLEMENT:SET-1:CONFIRMED`) — dana PG → bank platform:

| Account | Owner | Direction | Amount |
|---|---|---|---|
| `BANK_OPERATING` (1200) | BANK-1 | DEBIT | 100.000 |
| `PG_CLEARING_RECEIVABLE` (1100) | MIDTRANS | CREDIT | 100.000 |

*(Mode portofolio: `actual = expected`, jadi `variance = 0`. Kalau ada selisih nyata — di produksi — dibukukan ke `FUND_TRANSFER_VARIANCE` (5900).)*

**Ledger Jurnal → J-3** (`SETTLEMENT:SET-1:RELEASE:USER-123`) — creator PENDING → AVAILABLE:

| Account | Owner | Direction | Amount |
|---|---|---|---|
| `CREATOR_PAYABLE_PENDING` (2100) | USER-123 | DEBIT | 93.340 |
| `CREATOR_PAYABLE_AVAILABLE` (2110) | USER-123 | CREDIT | 93.340 |

Sekarang creator punya saldo **available 93.340** dan boleh menarik.

---

## Step 3 — Fund Transfer (bank platform → payout provider/Flip)

Sebelum bisa payout, platform top-up float Flip dari rekening bank (operasional).

**DB state**:
```sql
-- payment.fund_transfers
INSERT (id='FT-1', direction='TO_PAYOUT_PROVIDER', source_type='BANK',
        target_type='PAYOUT_PROVIDER', counterparty_provider_id=2 /*FLIP*/,
        sent_amount=500500, status='COMPLETED', bank_reference=…);
```

**Ledger Jurnal → J-4** (`FUND_TRANSFER:FT-1`):

| Account | Owner | Direction | Amount |
|---|---|---|---|
| `PAYOUT_PROVIDER_FLOAT` (1300) | FLIP | DEBIT | 500.500 |
| `BANK_OPERATING` (1200) | BANK-1 | CREDIT | 500.500 |

---

## Step 4 — Withdrawal HOLD (creator tarik dana)

Creator `USER-123` request withdraw Rp 90.000; fee platform Rp 3.000, net diterima Rp 87.000.

**DB state**:
```sql
-- payment.withdrawals
INSERT (id='WD-1', user_id='USER-123', destination_id=…, status='REQUESTED',
        requested_amount=90000, withdrawal_fee_amount=3000, net_disbursement_amount=87000, …);
```

**Ledger Jurnal → J-5** (`WITHDRAWAL:WD-1:HOLD`) — available di-hold:

| Account | Owner | Direction | Amount |
|---|---|---|---|
| `CREATOR_PAYABLE_AVAILABLE` (2110) | USER-123 | DEBIT | 90.000 |
| `WITHDRAWAL_PAYABLE` (2200) | USER-123 | CREDIT | 90.000 |

---

## Step 5 — Payout via Flip (COMPLETED)

`PayoutService.processPayout(WD-1)` → Flip disbursement. Fee provider (Flip) Rp 2.500.

**DB state**:
```sql
-- payment.payouts
INSERT (id='PO-1', withdrawal_id='WD-1', provider_id=2 /*FLIP*/, status='COMPLETED',
        amount=87000, provider_fee_amount=2500, provider_reference_id='<flip ref>', …);
-- payment.withdrawals.status='PAID', completed_at=…
```

**Ledger Jurnal → J-6** (`PAYOUT:PO-1:COMPLETED`):

| Account | Owner | Direction | Amount |
|---|---|---|---|
| `WITHDRAWAL_PAYABLE` (2200) | USER-123 | DEBIT | 90.000 |
| `PAYOUT_FEE_EXPENSE` (5100) | – | DEBIT | 2.500 |
| `WITHDRAWAL_FEE_REVENUE` (4100) | – | CREDIT | 3.000 |
| `PAYOUT_PROVIDER_FLOAT` (1300) | FLIP | CREDIT | 89.500 |

Σ DEBIT = 92.500 = Σ CREDIT ✓. Float Flip berkurang = 87.000 (ke creator) + 2.500 (fee provider).

---

## Ringkasan Saldo (subset)

| Account (code) | Owner | Saldo akhir |
|---|---|---|
| `PG_CLEARING_RECEIVABLE` (1100) | MIDTRANS | 0 |
| `BANK_OPERATING` (1200) | BANK-1 | 100.000 − 500.500 = −400.500 |
| `PAYOUT_PROVIDER_FLOAT` (1300) | FLIP | 500.500 − 89.500 = 411.000 |
| `CREATOR_PAYABLE_PENDING` (2100) | USER-123 | 0 |
| `CREATOR_PAYABLE_AVAILABLE` (2110) | USER-123 | 93.340 − 90.000 = 3.340 |
| `WITHDRAWAL_PAYABLE` (2200) | USER-123 | 0 |
| `VAT_PAYABLE` (2300) | – | 660 |
| `PLATFORM_FEE_REVENUE` (4000) | – | 6.000 |
| `WITHDRAWAL_FEE_REVENUE` (4100) | – | 3.000 |
| `PAYOUT_FEE_EXPENSE` (5100) | – | 2.500 |

> Semua jurnal **SELALU** balanced (ΣDEBIT = ΣCREDIT). Ledger bersifat append-only — koreksi lewat jurnal reversal, bukan edit.
