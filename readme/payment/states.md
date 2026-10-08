# Payment — Peta Status

> Semua status resmi di modul `payment`, artinya, dan siapa yang mengubahnya.
> Konsep & alur: [overview.md](./overview.md) · Settlement:
> [settlement.md](./settlement.md) · Istilah: [Glosarium](../glossary.md).

---

## Siapa yang mengubah status?

| Pemicu | Bahasa manusia | Contoh status yang diubah |
|---|---|---|
| **Webhook provider** | Midtrans/Flip mengirim kabar | `payments.status`, `payment_attempts.status` |
| **Job Quartz** | settlement otomatis (T+n) & proses terjadwal | `settlements.status`, `payouts.status` |
| **User/creator** | pengguna minta tarik dana | `withdrawals.status` |
| **Sistem** | proses internal | pembuatan batch, idempotency |

> Status **tidak** diubah di controller. Selalu lewat service (`@Transactional`).

---

## 1. `payments.status` — status pembayaran

| Status | Arti | Bisa pindah ke |
|---|---|---|
| `INITIATED` | pembayaran dibuat | PENDING/PAID/EXPIRED/FAILED/CANCELLED |
| `PENDING` | dikirim ke PG, menunggu bayar | PAID/EXPIRED/FAILED/CANCELLED |
| `PAID` | pembayar sudah bayar | (refund → lihat bawah) |
| `EXPIRED` | waktu bayar habis | – |
| `FAILED` | gagal di PG | – |
| `CANCELLED` | dibatalkan | – |
| `PARTIALLY_REFUNDED` / `REFUNDED` | enum ada, fitur refund belum | – |

```
INITIATED ─► PENDING ─► PAID
    │            ├─► EXPIRED
    │            ├─► FAILED
    │            └─► CANCELLED
    └────────────► (langsung PAID bila charge instan)

PAID ─► PARTIALLY_REFUNDED ─► REFUNDED    (belum diimplementasikan)
```

Method entity: `markPending()`, `markPaid(at)`, `markExpired(at)`, `markFailed()`,
`markCancelled(at)`, `scheduleSettlement(date)`, `markSettled(settlementId, at)`.

Jurnal saat `PAID`: **J-1**.

---

## 2. `payment_attempts.status` — per percobaan bayar

`INITIATED` → `PENDING` → `PAID` / `EXPIRED` / `FAILED`. Satu pembayaran bisa punya banyak
attempt (retry), tapi hanya satu aktif per `(payment, route)`.
`provider_reference_id` = `Order ID` Midtrans, dipakai matching webhook.

---

## 3. `settlements.status` — batch pencairan (otomatis)

Batch dibuat **otomatis job Quartz** untuk payment `PAID` yang sudah lewat
`expected_settlement_date` (T+n hari kerja). Batch dibuat lalu langsung dikonfirmasi
dalam satu run.

| Status | Arti | Yang terjadi |
|---|---|---|
| `PENDING` | batch baru dibuat (transien) | belum ada jurnal |
| `CONFIRMED` | dana dianggap cair | posting **J-2** + **J-3** |
| `CANCELLED` | dibatalkan sebelum dana masuk | tidak ada jurnal |

Method entity: `matchExpected(expectedAmount)`, `confirm(actualAmount, at)`, `cancel()`.

---

## 4. `withdrawals.status`

| Status | Arti | Jurnal |
|---|---|---|
| `REQUESTED` | creator minta tarik | **J-5** (hold) |
| `PROCESSING` | sedang dikerjakan provider | – |
| `PAID` | uang terkirim | **J-6** |
| `FAILED` | gagal dikirim | **J-7** (kembalikan hold) |
| `CANCELLED` / `REJECTED` | dibatalkan/ditolak | – |

---

## 5. `payouts.status`

| Status | Arti |
|---|---|
| `PENDING` | payout dibuat |
| `COMPLETED` | berhasil (→ J-6, withdrawal `PAID`) |
| `FAILED` | gagal (→ J-7, withdrawal `FAILED`) |
| `REVERSED` | dibalik setelah sempat berhasil |

---

## 6. `refunds.status`

`REQUESTED` → `PROCESSING` → `SUCCEEDED` / `FAILED`. (Belum diimplementasikan.)

---

## 7. `fund_transfers.status`

`PENDING` → `IN_TRANSIT` → `COMPLETED` / `FAILED`. Jurnal **J-4**.

---

## 8. `adjustments.status`

`PENDING` → `POSTED` / `REJECTED`. Aturan maker-checker: `requested_by != approved_by`.

---

## 9. `processed_events` — pengaman idempotency

Bukan state machine, tapi inbox webhook: unique `(provider_id, external_event_id)`
memastikan event yang sama diproses sekali. Jenis: `PAYMENT_PAID`, `PAYMENT_EXPIRED`,
`PAYMENT_FAILED`, `PAYOUT_COMPLETED`, `PAYOUT_FAILED`, `SETTLEMENT`.

---

## Ringkasan status ↔ jurnal

| Momen | Status | Jurnal |
|---|---|---|
| Pembayaran dibayar | `payments=PAID` | **J-1** |
| Batch settlement dikonfirmasi | `settlements=CONFIRMED` | **J-2**, **J-3** |
| Fund transfer | `fund_transfers=COMPLETED` | **J-4** |
| Creator minta tarik | `withdrawals=REQUESTED` | **J-5** |
| Payout sukses | `payouts=COMPLETED` | **J-6** |
| Payout gagal | `payouts=FAILED` | **J-7** |
| Refund | `refunds=SUCCEEDED` | **J-8/J-9** |
| Adjustment | `adjustments=POSTED` | **J-10** |
| Chargeback | – | **J-11** |
