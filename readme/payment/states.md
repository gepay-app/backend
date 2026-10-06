# Peta State (Status) — Modul `payment`

> Semua status resmi di modul pembayaran, artinya dalam bahasa manusia, dan siapa
> yang boleh mengubahnya. Ini peta yang bikin "banyak state" jadi jelas.
>
> Konsep & istilah: [Glosarium](../glossary.md) · Alur uang:
> [payment-example.md](./payment-example.md) · Operasional:
> [manual-settlement.md](./manual-settlement.md).

---

## Siapa yang mengubah status?

| Pemicu | Bahasa manusia | Contoh status yang diubah |
|---|---|---|
| **Webhook provider** | Midtrans/Flip mengirim kabar | `payments.status`, `payment_attempts.status` |
| **Admin** | manusia memverifikasi bukti | `settlements.status` (konfirmasi) |
| **Job Quartz** | tugas terjadwal/cluster | `payouts.status`, monitor overdue |
| **User/creator** | pengguna minta tarik dana | `withdrawals.status` |
| **Sistem** | proses internal | pembuatan batch, idempotency |

> Status **tidak** diubah langsung di controller. Selalu lewat service
> (`@Transactional`), sesuai [`../../AGENTS.md`](../../AGENTS.md).

---

## 1. `payments.status` — status donasi/transaksi

Status yang **sudah bisa berpindah** (ada method di entity `Payment`):

| Status | Arti awam | Bisa pindah ke |
|---|---|---|
| `INITIATED` | donasi dibuat, belum ada instruksi bayar | PENDING, PAID, EXPIRED, FAILED, CANCELLED |
| `PENDING` | sudah dikirim ke PG, menunggu donatur bayar | PAID, EXPIRED, FAILED, CANCELLED |
| `PAID` | donatur **sudah bayar** (uang ada di PG) | (refund/chargeback → lihat di bawah) |
| `EXPIRED` | waktu bayar habis | — |
| `FAILED` | gagal di sisi PG | — |
| `CANCELLED` | dibatalkan | — |

Status yang **belum diimplementasikan** (ada di enum, menunggu fitur refund):

| Status | Arti awam |
|---|---|
| `PARTIALLY_REFUNDED` | sebagian dana dikembalikan ke donatur |
| `REFUNDED` | seluruh dana dikembalikan |

Perpindahan:

```
INITIATED ─► PENDING ─► PAID
    │            │
    │            ├─► EXPIRED
    │            ├─► FAILED
    │            └─► CANCELLED
    └────────────► (langsung PAID bila charge instan)

PAID ─► PARTIALLY_REFUNDED ─► REFUNDED     (belum diimplementasikan)
```

Method entity: `markPending()`, `markPaid(paidAt)`, `markExpired(expiredAt)`,
`markFailed()`, `markCancelled(cancelledAt)`, `scheduleSettlement(date)`,
`markSettled(settlementId, at)`.

Jurnal terkait: saat `PAID` → **J-1** (hak creator masuk `PENDING`).

---

## 2. `payment_attempts.status` — per percobaan bayar

Satu donasi bisa punya banyak percobaan (retry / ganti channel). Hanya **satu
attempt aktif** per `(payment, route)` (unique index).

| Status | Arti awam |
|---|---|
| `INITIATED` | attempt dibuat |
| `PENDING` | menunggu pembayaran |
| `PAID` | attempt ini yang berhasil dibayar |
| `EXPIRED` | attempt kedaluwarsa (boleh bikin attempt baru) |
| `FAILED` | attempt ini gagal |

Kunci matching ke webhook/CSV Midtrans: `provider_reference_id` = kolom
`Order ID` Midtrans.

---

## 3. `settlements.status` — batch pencairan dari PG ke bank

Batch dibuat **manual dari bukti**, bukan dari webhook (lihat
[manual-settlement.md](./manual-settlement.md)).

| Status | Arti awam | Yang terjadi |
|---|---|---|
| `PENDING` | admin baru membuat batch, belum dikonfirmasi | belum ada jurnal |
| `CONFIRMED` | bukti dana masuk sudah diverifikasi | posting **J-2** + **J-3** |
| `CANCELLED` | dibatalkan sebelum dana masuk | tidak ada jurnal |

Method entity: `matchExpected(expectedAmount)`, `confirm(actualAmount, at)`,
`cancel()`. `variance_amount = actual_amount − expected_amount`.

```
(PENDING) ──confirm(bukti)──► CONFIRMED ──► J-2 (dana ke bank) + J-3 (creator PENDING→AVAILABLE)
    │
    └─cancel()──► CANCELLED
```

---

## 4. `withdrawals.status` — permintaan tarik dana creator

| Status | Arti awam | Jurnal |
|---|---|---|
| `REQUESTED` | creator minta tarik | **J-5** (available → hold) |
| `PROCESSING` | sedang dikerjakan provider payout | — |
| `PAID` | uang sudah terkirim ke creator | **J-6** |
| `FAILED` | gagal dikirim | **J-7** (kembalikan hold ke available) |
| `CANCELLED` | dibatalkan (belum hold) | — |
| `REJECTED` | ditolak admin | — |

> Baru `create()` → `REQUESTED` yang ada di kode. Transisi lain menyusul di
> Milestone 4 ([todo.md](./todo.md)).

---

## 5. `payouts.status` — eksekusi pengiriman uang via Flip

| Status | Arti awam |
|---|---|
| `PENDING` | payout dibuat, menunggu dikirim |
| `COMPLETED` | berhasil (→ J-6, withdrawal `PAID`) |
| `FAILED` | gagal (→ J-7, withdrawal `FAILED`) |
| `REVERSED` | dibalik setelah sempat berhasil |

---

## 6. `refunds.status` — pengembalian dana

| Status | Arti awam |
|---|---|
| `REQUESTED` | refund diminta |
| `PROCESSING` | sedang diproses PG |
| `SUCCEEDED` | berhasil dikembalikan |
| `FAILED` | gagal |

(Implementasi menyusul di Milestone 5.)

---

## 7. `fund_transfers.status` — pindah dana antar akun platform

Bukan pendapatan/biaya, hanya memindahkan uang kita sendiri (mis. bank → Flip).

| Status | Arti awam |
|---|---|
| `PENDING` | perintah dibuat |
| `IN_TRANSIT` | sedang dalam perjalanan |
| `COMPLETED` | sudah diterima |
| `FAILED` | gagal |

Jurnal: **J-4** (`FUND_TRANSFER`, mis. `PAYOUT_PROVIDER_FLOAT` naik,
`BANK_OPERATING` turun).

---

## 8. `adjustments.status` — koreksi manual (maker-checker)

| Status | Arti awam |
|---|---|
| `PENDING` | diajukan, menunggu persetujuan |
| `POSTED` | disetujui & jurnal diposting |
| `REJECTED` | ditolak |

Aturan: pembuat ≠ penyetuju (`requested_by IS DISTINCT FROM approved_by`).

---

## 9. `processed_events` — bukan status, tapi pengaman

Bukan state machine, melainkan **inbox idempotency webhook**: unique
`(provider_id, external_event_id)` memastikan event yang sama diproses sekali.

Jenis event (enum `ProcessedEventType`): `PAYMENT_PAID`, `PAYMENT_EXPIRED`,
`PAYMENT_FAILED`, `PAYOUT_COMPLETED`, `PAYOUT_FAILED`, `SETTLEMENT`.

---

## Ringkasan: status ↔ jurnal ledger

| Momen | Status `payment` terkait | Jurnal |
|---|---|---|
| Donasi dibayar | `payments=PAID` | **J-1** |
| Batch settlement dikonfirmasi | `settlements=CONFIRMED` | **J-2**, **J-3** |
| Fund transfer (bank → Flip) | `fund_transfers=COMPLETED` | **J-4** |
| Creator minta tarik | `withdrawals=REQUESTED` | **J-5** |
| Payout sukses | `payouts=COMPLETED`, `withdrawals=PAID` | **J-6** |
| Payout gagal | `payouts=FAILED`, `withdrawals=FAILED` | **J-7** |
| Refund | `refunds=SUCCEEDED` | **J-8/J-9** |
| Adjustment | `adjustments=POSTED` | **J-10** |
| Chargeback | — | **J-11** |

Detail jurnal: [../ledger/ledger-example.md](../ledger/ledger-example.md).
