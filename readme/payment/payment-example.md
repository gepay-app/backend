# Payment Module — End-to-End Flow Examples

> Pendamping [`payment-design.md`](./payment-design.md) & [`ledger-example.md`](../ledger/ledger-example.md).  
> Menunjukkan alur lengkap dari webhook hingga payout, dengan state database di setiap step.

---

## Asumsi Contoh

- Mata uang IDR, nominal `BIGINT` (rupiah bulat).
- ID disingkat: `PAY-1`, `SET-1`, `WD-1`, `FT-1`, `PO-1`, `RF-1`, `ADJ-1`.
- Jurnal: `J-1`..`J-11` (lihat `ledger-example.md`).
- PG fee di-pass-through ke donatur. Fee platform dipotong dari donasi.

---

## Seed Data (Ringkas)

### Providers
| id | code | name | payin | payout |
|----|------|------|-------|--------|
| 1 | MIDTRANS | Midtrans | ✓ | ✗ |
| 2 | FLIP | Flip | ✗ | ✓ |

### Channels
| id | provider | code | name | type |
|----|----------|------|------|------|
| 1 | MIDTRANS | BCA_VA | BCA Virtual Account | VA |
| 2 | FLIP | BANK_BCA | Bank BCA Transfer | BANK_TRANSFER |

### Channel Routes
| id | provider | channel | fee_config_id |
|----|----------|---------|---------------|
| 101 | MIDTRANS | BCA_VA | 501 |

### Fee Configs (Rate Card)
| id | version | platform_fee_bps | pg_fee_bps | vat_rate_bps | payout_fee_flat | payout_fee_bps | effective_from |
|----|---------|------------------|------------|--------------|-----------------|----------------|----------------|
| 501 | 1 | 600 | 290 | 110 | 2500 | 50 | 2024-01-01 |

---

## Scenario 1: Donasi Berhasil (End-to-End)

### Step 1: Webhook `payment.paid` dari Midtrans
**Input**: `payment_id=PAY-1`, `amount=100000`, `channel=BCA_VA`, `paid_at=2024-01-15T10:00:00Z`

**Payment DB State**:
```sql
-- payment.payments
INSERT VALUES (id='PAY-1', amount=100000, status='PAID', channel_route_id=101, 
               fee_snapshot='{"platform_fee_bps":600,"pg_fee_bps":290,"vat_rate_bps":110}',
               paid_at='2024-01-15T10:00:00Z');

-- payment.payment_attempts
INSERT VALUES (id='ATT-1', payment_id='PAY-1', channel_id=1, amount=100000, status='SUCCESS');

-- payment.processed_events
INSERT VALUES (id='PE-1', idempotency_key='MIDTRANS:PAY-1:PAID', event_type='payment.paid');
```

**Ledger Jurnal** → **J-1** (lihat `ledger-example.md`):
- DEBIT PG_CLEARING_RECEIVABLE (MIDTRANS) 100.000
- CREDIT CREATOR_PAYABLE_PENDING (USER-123) 93.340
- CREDIT PLATFORM_FEE_REVENUE 6.000
- CREDIT VAT_PAYABLE 660

---

### Step 2: Settlement Dikonfirmasi (Laporan Midtrans Diterima)
**Input**: `settlement_id=SET-1`, `provider=MIDTRANS`, `amount=100000`, `period=2024-01-15`, `evidence_file=...`

**Payment DB State**:
```sql
-- payment.settlements
INSERT VALUES (id='SET-1', provider_id=1, amount=100000, status='CONFIRMED',
               evidence_ref='s3://bucket/set-1.json', confirmed_at='2024-01-17T09:00:00Z');

-- payment.settlement_items
INSERT VALUES (settlement_id='SET-1', payment_attempt_id='ATT-1', amount=100000);
```

**Ledger Jurnal** → **J-2**:
- DEBIT PAYIN_PROVIDER_BALANCE (MIDTRANS) 100.000
- CREDIT PG_CLEARING_RECEIVABLE (MIDTRANS) 100.000

**Ledger Jurnal** → **J-3** (Release Creator):
- DEBIT CREATOR_PAYABLE_PENDING (USER-123) 93.340
- CREDIT CREATOR_PAYABLE_AVAILABLE (USER-123) 93.340

---

### Step 3: Creator Request Withdraw
**Input**: `creator=USER-123`, `amount=500000`, `destination=DEST-1 (BCA 1234567890)`

**Payment DB State**:
```sql
-- payment.withdrawals
INSERT VALUES (id='WD-1', user_id='USER-123', amount=500000, status='PENDING',
               payout_destination_id=1, requested_at='2024-01-20T14:00:00Z');
```

**Ledger Jurnal** → **J-4**:
- DEBIT CREATOR_PAYABLE_AVAILABLE (USER-123) 500.000
- CREDIT WITHDRAWAL_PAYABLE (USER-123) 500.000

---

### Step 4: Payout ke Flip (Quartz Job)
**Processing**: `PayoutService.processPayout(WD-1)` → call Flip API

**Payment DB State**:
```sql
-- payment.payouts
INSERT VALUES (id='PO-1', withdrawal_id='WD-1', amount=500000, fee=3000,
               status='COMPLETED', flip_transfer_id='FLIP-TRF-123',
               completed_at='2024-01-20T14:05:00Z');

-- payment.withdrawals UPDATE status='COMPLETED'
```

**Ledger Jurnal** → **J-6**:
- DEBIT WITHDRAWAL_PAYABLE (USER-123) 500.000
- DEBIT PAYOUT_FEE_EXPENSE 3.000
- CREDIT WITHDRAWAL_FEE_REVENUE 2.500
- CREDIT PAYOUT_PROVIDER_FLOAT (FLIP) 500.500

---

## Scenario 2: Fund Transfer (Top-up Midtrans → Flip)

**Input**: `fund_transfer_id=FT-1`, `from_provider=MIDTRANS`, `to_provider=FLIP`, `amount=503000`

**Payment DB State**:
```sql
-- payment.fund_transfers
INSERT VALUES (id='FT-1', from_provider_id=1, to_provider_id=2, amount=503000,
               status='COMPLETED', bank_transfer_ref='TRF-20240120-001',
               completed_at='2024-01-20T11:00:00Z');
```

**Ledger Jurnal** → **J-5**:
- DEBIT PAYOUT_PROVIDER_FLOAT (FLIP) 503.000
- CREDIT PAYIN_PROVIDER_BALANCE (MIDTRANS) 503.000

---

## Scenario 3: Refund Setelah Settlement

**Input**: `refund_id=RF-1`, `payment_id=PAY-1`, `reason=DONOR_REQUEST`, `original_journal_id=J-1`

**Payment DB State**:
```sql
-- payment.refunds
INSERT VALUES (id='RF-1', payment_id='PAY-1', amount=100000, status='COMPLETED',
               midtrans_refund_id='MID-REF-123', processed_at='2024-01-25T10:00:00Z');
```

**Ledger Jurnal** → **J-8** (Reversal):
- DEBIT CREATOR_PAYABLE_AVAILABLE (USER-123) 93.340
- DEBIT PLATFORM_FEE_REVENUE 6.000
- DEBIT VAT_PAYABLE 660
- CREDIT PAYIN_PROVIDER_BALANCE (MIDTRANS) 100.000
- `reverses_journal_id = J-1`

---

## Scenario 4: Refund Sebelum Settlement (Hanya Pending)

**Input**: `refund_id=RF-2`, `payment_id=PAY-2` (belum di-settle), `original_journal_id=J-1b`

**Ledger Jurnal** → **J-9**:
- DEBIT CREATOR_PAYABLE_PENDING (USER-123) 93.340
- DEBIT PLATFORM_FEE_REVENUE 6.000
- DEBIT VAT_PAYABLE 660
- CREDIT PG_CLEARING_RECEIVABLE (MIDTRANS) 100.000
- `reverses_journal_id = J-1b`

---

## Scenario 5: Adjustment (Koreksi Selisih Rekonsiliasi)

**Input**: `adjustment_id=ADJ-1`, `maker=USER-ADMIN`, `approver=USER-FINANCE`, `variance=500` (MIDTRANS kurang)

**Payment DB State**:
```sql
-- payment.adjustments
INSERT VALUES (id='ADJ-1', type='VARIANCE', provider_id=1, amount=500,
               status='APPROVED', maker_id='USER-ADMIN', approver_id='USER-FINANCE',
               approved_at='2024-01-31T10:00:00Z');
```

**Ledger Jurnal** → **J-10**:
- DEBIT FUND_TRANSFER_VARIANCE 500
- CREDIT PAYIN_PROVIDER_BALANCE (MIDTRANS) 500

---

## Scenario 6: Chargeback (Setelah Withdraw)

**Input**: `chargeback_id=CB-1`, `payment_id=PAY-1`, `amount=100000`, `creator_already_withdrawn=true`

**Payment DB State**:
```sql
-- payment.chargebacks (extension table)
INSERT VALUES (id='CB-1', payment_id='PAY-1', amount=100000, status='RECEIVED',
               received_at='2024-02-01T10:00:00Z');
```

**Ledger Jurnal** → **J-11**:
- DEBIT REFUND_CHARGEBACK_LOSS 100.000
- CREDIT PAYIN_PROVIDER_BALANCE (MIDTRANS) 100.000
- DEBIT CREATOR_NEGATIVE_BALANCE (USER-123) 93.340
- CREDIT CREATOR_PAYABLE_AVAILABLE (USER-123) 93.340

> **Note**: `CREATOR_NEGATIVE_BALANCE` (5300) = piutang ke creator untuk clawback.  
> Collector job akan tagih creator atau potong dari future earnings.

---

## Database State Summary (After All Scenarios)

### `ledger.accounts` (Subset)
| code | owner_type | owner_ref | balance | version |
|------|------------|-----------|---------|---------|
| 1100 | PAYMENT_PROVIDER | MIDTRANS | 0 | 3 |
| 1150 | PAYMENT_PROVIDER | MIDTRANS | 503.000 | 2 |
| 1200 | BANK | BANK-1 | 0 | 1 |
| 1300 | PAYOUT_PROVIDER | FLIP | 500.500 | 2 |
| 2100 | USER | USER-123 | 0 | 2 |
| 2110 | USER | USER-123 | 406.660* | 4 |
| 2200 | USER | USER-123 | 0 | 2 |
| 2300 | NULL | NULL | 0 | 2 |
| 4000 | NULL | NULL | 0 | 2 |
| 4100 | NULL | NULL | 2.500 | 1 |
| 5000 | NULL | NULL | 0 | 1 |
| 5100 | NULL | NULL | 3.000 | 1 |
| 5200 | NULL | NULL | 100.000 | 1 |
| 5300 | USER | USER-123 | 93.340 | 1 |
| 5900 | NULL | NULL | 500 | 1 |

\* `2110` balance = 93.340 (from PAY-1) - 500.000 (WD-1) + 93.340 (refund RF-1 clawback) + 406.660... hitung manual ya.

### `ledger.journals`
| id | idempotency_key | reference_type | reference_id | reverses_journal_id |
|----|-----------------|----------------|--------------|---------------------|
| J-1 | PAYMENT:PAY-1:PAID | PAYMENT | PAY-1 | NULL |
| J-2 | SETTLEMENT:SET-1:CONFIRMED | SETTLEMENT | SET-1 | NULL |
| J-3 | SETTLEMENT:SET-1:RELEASE | SETTLEMENT | SET-1 | NULL |
| J-4 | WITHDRAWAL:WD-1:HOLD | WITHDRAWAL | WD-1 | NULL |
| J-5 | FUND_TRANSFER:FT-1 | FUND_TRANSFER | FT-1 | NULL |
| J-6 | PAYOUT:PO-1:COMPLETED | PAYOUT | PO-1 | NULL |
| J-8 | REFUND:RF-1 | REFUND | RF-1 | J-1 |
| J-9 | REFUND:RF-2 | REFUND | RF-2 | J-1b |
| J-10 | ADJUSTMENT:ADJ-1 | ADJUSTMENT | ADJ-1 | NULL |
| J-11 | CHARGEBACK:CB-1 | ADJUSTMENT | CB-1 | NULL |

---

## Verifikasi Balance (Audit Trail)

Setiap akun: `balance = Σ(DEBIT entries) - Σ(CREDIT entries)` untuk tipe ASSET/EXPENSE,  
atau `Σ(CREDIT) - Σ(DEBIT)` untuk LIABILITY/REVENUE/EQUITY.

Contoh verifikasi `CREATOR_PAYABLE_AVAILABLE (2110)` untuk `USER-123`:
- J-3: CREDIT +93.340
- J-4: DEBIT -500.000
- J-8: DEBIT -93.340 (refund clawback)
- J-11: CREDIT +93.340 (chargeback clawback)
- **Total = -406.660** → Liability berarti credit normal, jadi balance = 406.660 kredit (utang ke creator)

Semua jurnal **SELALU** balanced (ΣDEBIT = ΣCREDIT) di level database.
