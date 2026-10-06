# Payment Module — Aturan Bisnis & Desain

> **Baca ini kalau** kamu ingin tahu *aturan* pembayaran: donasi, pencairan dana
> dari PG, tarik dana creator. Untuk istilah, lihat [Glosarium](../glossary.md);
> untuk daftar status, lihat [states.md](./states.md).
>
> Inti: modul `payment` **memutuskan kapan uang bergerak**, tetapi **angka uang
> hanya ditulis oleh `ledger`** lewat `LedgerApi`. Payment **tidak pernah** menulis
> jurnal sendiri.

---

## 1. Prinsip inti

| Prinsip | Bahasa manusia |
|---|---|
| **Vendor-blind** | Kode bisnis tidak menyebut Midtrans/Flip. Bahasa vendor diterjemahkan oleh **adapter**. Tambah PG = tambah adapter, bukan ubah service. |
| **Payment ≠ ledger** | Payment hanya menyusun "gerakan apa"; ledger yang mencatat "saldo siapa berubah berapa". |
| **Idempotency dari pemanggil** | Payment membuat `idempotencyKey` (`TYPE:ID:ACTION`) supaya webhook dobel tidak memposting dua kali. |
| **Settlement berbasis bukti** | Jurnal pencairan **hanya** diposting kalau ada bukti dana masuk (mutasi bank/laporan), bukan karena timer T+n. |

---

## 2. Empat konsep yang sering tercampur

Ini kunci memahami modul ini. "Settlement" bisa berarti hal berbeda tergantung
siapa yang bicara (detail: [Glosarium](../glossary.md#settlement-punya-3-arti)):

| # | Konsep | Bahasa manusia | Sumber sinyal | Di sistem kita |
|---|---|---|---|---|
| 1 | **Status transaksi PG** | vocab vendor (`pending`, `settlement`, `expire`, `deny`, `cancel`) | webhook/API vendor | dipetakan adapter |
| 2 | **Paid** | donatur sudah bayar; PG berutang ke kita | webhook (status #1 dipetakan) | `payments.status = PAID` |
| 3 | **Withdrawable di PG** | saldo di PG sudah boleh ditarik ke bank | dashboard MAP / laporan disbursement | **tidak** ada kolomnya — hanya dipakai admin |
| 4 | **Settlement kita** | uang benar-benar masuk rekening bank | **bukti**: mutasi bank/CSV/MAP | `settlements` (batch) → J-2 + J-3 |

**Aturan penting**: transaksi PG cukup berhenti di **Paid** (#2). Konsep #4 hidup di
agregat terpisah (`settlements`) dan **hanya** dikonfirmasi oleh bukti. Midtrans
memakai kata `settlement` untuk #1 — jangan disamakan dengan #4.

---

## 3. Entitas utama (`V5__payment_tables.sql`)

| Tabel | Fungsi (awam) |
|---|---|
| `providers` | Daftar penyedia: MIDTRANS (payin), FLIP (payout) |
| `channels` | Metode bayar netral vendor: `VA_BCA`, `QRIS`, `BANK_BCA`, ... |
| `channel_routes` | "Channel X dilayani provider Y", + kebijakan settlement (`settlement_delay_days` = T+n hari kerja, `settlement_target`) |
| `holidays` | Hari libur nasional (untuk hitung hari kerja) |
| `fee_configs` | Daftar tarif ber-versi (platform/gateway/payout) |
| `user_fee_overrides` | Tarif khusus per user (VIP) |
| `payments` | Transaksi donasi/konten (lihat [states.md](./states.md)) |
| `payment_attempts` | Percobaan bayar; `provider_reference_id` = `Order ID` Midtrans |
| `settlements` | **Batch** pencairan PG → bank (berbasis bukti) |
| `refunds` | Pengembalian dana |
| `withdrawals` | Permintaan tarik dana creator |
| `payout_destinations` | Rekening tujuan creator |
| `payouts` | Eksekusi pengiriman uang via Flip |
| `fund_transfers` | Pindah dana antar akun platform (bank → Flip) |
| `adjustments` | Koreksi manual (maker-checker) |
| `processed_events` | Inbox idempotency webhook |
| `reconciliation_runs` / `_items` | Rekonsiliasi (fase lanjut) |

Catatan:
- `payments` menyimpan **snapshot** provider/channel/route + semua komponen fee,
  supaya audit tetap utuh walau tarif berubah.
- `payments.settlement_id` menghubungkan payment ke batch-nya. **Tidak ada** tabel
  `settlement_items`; 1 payment selalu masuk 1 batch.

---

## 4. Adapter provider (SPI)

```
internal/provider/
├── PaymentProviderClient.java      # payin: createCharge, verifySignature, parseEvent
├── PayoutProviderClient.java       # payout: disburse, verifySignature, parseEvent
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

## 5. Settlement = batch berbasis bukti

Midtrans **tidak** mengirim sinyal "dana sudah bisa ditarik". Pencairan ke bank
dilakukan manual dari MAP, dan baru boleh dilakukan setelah beberapa hari kerja
setelah transaksi settled. Karena itu:

1. `payments.expected_settlement_date` = `paid_at` + `T+n` **hari kerja**
   (`channel_routes.settlement_delay_days` + `BusinessDayCalculator` + `holidays`).
2. Tanggal itu **hanya monitor** (job OVERDUE), **bukan** pemicu jurnal.
3. Bukti **keanggotaan** (order mana): CSV/report → match `Order ID`
   (= `payment_attempts.provider_reference_id`).
4. Bukti **nominal** (berapa): mutasi bank / konfirmasi MAP → `actual_amount`.
5. `settlements` menyimpan `expected_amount` (Σ yang di-match), `actual_amount`,
   `variance_amount`, dan `evidence_*`.
6. Konfirmasi batch (`CONFIRMED`) → posting **J-2** (dana ke bank) + **J-3**
   (creator `PENDING → AVAILABLE`).

PG yang punya webhook/report settlement sendiri cukup mengisi batch yang sama —
alur internal tidak berubah.

> Operasional langkah demi langkah, termasuk kenapa CSV harian **tidak** menjawab
> "sudah withdrawable" dan cara mencocokkan withdrawal lump-sum:
> [`manual-settlement.md`](./manual-settlement.md).

---

## 6. Idempotency key (payment → ledger)

Format: `{TYPE}:{ID}:{ACTION}`.

| Event | Format |
|---|---|
| Payment paid | `PAYMENT:{paymentId}:PAID` |
| Settlement confirm | `SETTLEMENT:{settlementId}:CONFIRMED` |
| Settlement release | `SETTLEMENT:{settlementId}:RELEASE` |
| Withdrawal hold | `WITHDRAWAL:{withdrawalId}:HOLD` |
| Payout completed / failed | `PAYOUT:{payoutId}:COMPLETED` / `:FAILED` |
| Fund transfer | `FUND_TRANSFER:{fundTransferId}` |
| Refund | `REFUND:{refundId}` |
| Adjustment | `ADJUSTMENT:{adjustmentId}` |

---

## 7. Konfigurasi modul

- **Package**: `com.gepe.gepay.payment` (CLOSED, `id="payment"`, `allowedDependencies = "ledger::api"`)
- **API**: `com.gepe.gepay.payment.api` (`@NamedInterface("api")`)
- **i18n**: `src/main/resources/i18n/payment/messages.properties` + `messages_id.properties`
- **Migrasi**: `V5__payment_tables.sql`
- **Expiry**: `payments`/`payment_attempts` default **1 jam** (configurable)
- **Hari kerja**: `payment.holidays` + `BusinessDayCalculator` (Sabtu/Minggu + libur)
- **Kredensial**: `PG_MIDTRANS_*`, `PG_FLIP_*` (via env, jangan commit)

---

## 8. Alur dana (ringkasan)

```
Donatur → PG (VA/QRIS) → webhook PAID (J-1: piutang)
    → batch settlement (bukti) → CONFIRMED
        J-2: PG_CLEARING_RECEIVABLE → BANK_OPERATING
        J-3: creator PENDING → AVAILABLE
    → fund transfer bank → payout provider (top-up Flip)   [J-4]
    → withdrawal HOLD (available → withdrawal payable)      [J-5]
    → payout via Flip → COMPLETED (hold lunas + fee)         [J-6]
```

Detail + angka: [`payment-example.md`](./payment-example.md).
Rencana kerja berurut: [`todo.md`](./todo.md).
