# Ledger — Konsep & Cara Kerja

> **Baca ini kalau** kamu ingin paham "buku besar" tanpa detail kode. Jika istilah
> akuntansi terasa asing, buka dulu [Glosarium](../glossary.md).
>
> Urutan dokumen ledger:
> 1. **konsep & cara kerja** — dokumen ini
> 2. [contoh jurnal](./ledger-example.md) — angka nyata per kejadian
> 3. [kontrak API](./ledger-design.md) — signature & modul
> 4. [implementasi (as-built)](./ledger-implementation.md) — isi kode
>
> **`ledger` adalah buku besar double-entry (pencatatan berpasangan) yang
> terisolasi.** Ia bersifat **vendor-blind** (tidak tahu Midtrans/Flip/donasi) dan
> menjadi sumber kebenaran tunggal untuk semua aset, utang, pendapatan, dan beban
> platform.

---

## 1. Konsep & Prinsip Utama

1. **Double-Entry Bookkeeping**:
   - Setiap transaksi finansial direkam sebagai `Journal` yang terdiri dari minimal 2 baris `Entry`.
   - **Aturan Mutlak**: Total Debit harus persis sama dengan Total Kredit ($\sum \text{DEBIT} = \sum \text{KREDIT}$).

2. **Immutable & Append-Only**:
   - Tabel `journals` dan `entries` hanya menerima perintah `INSERT`.
   - **Tidak ada `UPDATE` atau `DELETE`** pada histori jurnal.
   - Koreksi kesalahan dilakukan dengan memposting **jurnal pembalikan (reversal)** baru via `reversesJournalId`.

3. **Lazy Account Creation**:
   - Akun global (seperti `PLATFORM_FEE_REVENUE` atau `VAT_PAYABLE`) di-seed di awal.
   - Akun per-entitas/owner (seperti saldo creator atau clearing provider) dibuat secara **lazy (otomatis saat pertama kali digunakan)** melalui `getOrCreateAccount`.

4. **Kinerja Bebas Deadlock (Deterministic Batch Locking)**:
   - Penguncian akun dilakukan secara batch mengurutkan ID Akun secara global (*ascending*).
   - Mencegah *circular wait* antar transaksi konkuren yang menyentuh kombinasi akun yang sama.

---

## 2. Idempotency Key — Aturan & Kepemilikan

### Mengapa Hanya Ada 1 Method `postJournal` dengan `idempotencyKey` Wajib?

`idempotencyKey` **HARUS di-generate oleh Caller (Modul Pemanggil, misal `payment`)**.

**Alasan Arsitektural:**
- Idempotensi adalah penjaminan bisnis. Hanya modul pemanggil yang paham identitas unik dari suatu event bisnis (contoh: `PAYMENT:PAY-123:PAID` atau `WITHDRAWAL:WD-999:HOLD`).
- Jika modul `ledger` mencoba membuat key sendiri (misalnya meng-hash isi deskripsi atau timestamp), saat terjadi *network retry* dari webhook/caller, perubahan kecil pada deskripsi atau waktu akan menyebabkan key berbeda dan **memicu pencatatan ganda (double-posting)**.
- Dengan mewajibkan caller memasok `idempotencyKey` unik berbasis Business ID + Action, jika request dipanggil 2x akibat retry, database `ledger` akan secara aman mengembalikan hasil jurnal yang sudah ada tanpa melakukan mutasi saldo ulang.

**Format Standar Idempotency Key:**
`{REFERENCE_TYPE}:{REFERENCE_ID}:{ACTION}`

*Contoh:*
- `PAYMENT:PAY-8812:PAID`
- `SETTLEMENT:SET-901:CONFIRMED`
- `WITHDRAWAL:WD-302:HOLD`
- `PAYOUT:PO-441:COMPLETED`

---

## 3. Chart of Accounts (`AccountCode` Enum)

Setiap akun diwakili oleh enum `AccountCode` yang menyimpan metadata resmi (Kode, Nama, Tipe, Saldo Normal, dan OwnerType):

| AccountCode | Kode | Tipe | Normal | OwnerType | Keterangan / Fungsi |
|-------------|------|------|--------|-----------|---------------------|
| `PG_CLEARING_RECEIVABLE` | `1100` | ASSET | DEBIT | `PAYMENT_PROVIDER` | Piutang ke PG payin (uang ditangkap PG, belum masuk bank kita) |
| `PAYIN_PROVIDER_BALANCE` | `1150` | ASSET | DEBIT | `PAYMENT_PROVIDER` | Uang di saldo akun PG payin (sudah masuk saldo PG, belum ditarik ke bank) |
| `BANK_OPERATING` | `1200` | ASSET | DEBIT | `BANK` | Rekening bank operasional utama platform |
| `PAYOUT_PROVIDER_FLOAT` | `1300` | ASSET | DEBIT | `PAYOUT_PROVIDER` | Float dana di payout provider (Flip) siap disburse |
| `FUND_TRANSFER_IN_TRANSIT` | `1400` | ASSET | DEBIT | `null` (Global) | Dana transit saat transfer antar provider/rekening |
| `CREATOR_PAYABLE_PENDING` | `2100` | LIABILITY | CREDIT | `USER` | Hak creator yang masih menunggu masa settlement |
| `CREATOR_PAYABLE_AVAILABLE` | `2110` | LIABILITY | CREDIT | `USER` | Hak creator yang **siap ditarik / withdraw** |
| `WITHDRAWAL_PAYABLE` | `2200` | LIABILITY | CREDIT | `USER` | Dana creator yang sedang di-hold saat proses withdrawal |
| `VAT_PAYABLE` | `2300` | LIABILITY | CREDIT | `null` (Global) | Utang PPN ke negara |
| `PLATFORM_FEE_REVENUE` | `4000` | REVENUE | CREDIT | `null` (Global) | Pendapatan fee transaksi platform |
| `WITHDRAWAL_FEE_REVENUE` | `4100` | REVENUE | CREDIT | `null` (Global) | Pendapatan fee penarikan dana |
| `PG_FEE_EXPENSE` | `5000` | EXPENSE | DEBIT | `null` (Global) | Beban fee Payment Gateway |
| `PAYOUT_FEE_EXPENSE` | `5100` | EXPENSE | DEBIT | `null` (Global) | Beban fee Payout Provider |
| `BANK_TRANSFER_FEE_EXPENSE` | `5150` | EXPENSE | DEBIT | `null` (Global) | Beban biaya transfer bank |
| `REFUND_CHARGEBACK_LOSS` | `5200` | EXPENSE | DEBIT | `null` (Global) | Kerugian akibat refund / chargeback |
| `CREATOR_NEGATIVE_BALANCE` | `5300` | ASSET | DEBIT | `USER` | Piutang ke creator (clawback jika refund setelah saldo ditarik) |
| `FUND_TRANSFER_VARIANCE` | `5900` | EXPENSE | DEBIT | `null` (Global) | Selisih/variansi transfer antar provider |

---

## 4. Kontrak Interface `LedgerApi`

Modul eksternal (terutama `payment`) hanya berinteraksi melalui interface `com.gepe.gepay.ledger.api.LedgerApi`:

```java
package com.gepe.gepay.ledger.api;

import com.gepe.gepay.ledger.api.dtos.*;
import com.gepe.gepay.ledger.api.enums.*;
import java.time.Instant;
import java.util.List;

public interface LedgerApi {

    /**
     * Dapatkan akun yang ada atau buat baru secara otomatis (lazy creation).
     */
    AccountDto getOrCreateAccount(AccountCode code, String ownerRef);

    /**
     * Dapatkan akun yang sudah ada. Lempar ServiceException (ACCOUNT_NOT_FOUND) jika tidak ada.
     */
    AccountDto getAccount(AccountCode code, String ownerRef);

    /**
     * Posting jurnal akuntansi berpasangan (double-entry).
     *
     * @param idempotencyKey    Key unik deterministik dari caller (contoh: "PAYMENT:PAY-1:PAID")
     * @param referenceType     Tipe referensi event bisnis (PAYMENT, SETTLEMENT, WITHDRAWAL, dll)
     * @param referenceId       ID bisnis (paymentId, settlementId, withdrawalId)
     * @param description       Deskripsi audit manusia
     * @param occurredAt        Waktu bisnis kejadian
     * @param lines             Minimal 2 baris entry (SUM DEBIT = SUM CREDIT)
     * @param reversesJournalId Nullable, ID jurnal yang dibalik jika ini jurnal pembalikan
     */
    PostJournalResult postJournal(
            String idempotencyKey,
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,
            Long reversesJournalId
    );

    /**
     * Query detail saldo akun (selalu dari DB — sengaja tidak di-cache).
     */
    AccountDto getBalance(AccountCode code, String ownerRef);

    /**
     * Query nominal saldo bersih akun (selalu dari DB — sengaja tidak di-cache).
     */
    long getBalanceAmount(AccountCode code, String ownerRef);
}
```

---

## 5. Contoh Penggunaan `LedgerApi`

### Contoh 1: Pembayaran Berhasil (Payment Paid)
Misal ada transaksi donasi **Rp 100.000** via Midtrans ke Creator `USER-123`.  
Fee platform = Rp 6.000, PPN = Rp 660, Net Creator = Rp 93.340.

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final LedgerApi ledgerApi;

    public void onPaymentPaid(String paymentId, String creatorUserId, String providerId, Instant paidAt) {
        String idemKey = "PAYMENT:" + paymentId + ":PAID";

        List<JournalLine> lines = List.of(
            // DEBIT: PG Clearing Receivable (Piutang ke Midtrans) Rp 100.000
            new JournalLine(AccountCode.PG_CLEARING_RECEIVABLE, providerId, EntryDirection.DEBIT, 100_000),

            // CREDIT: Creator Payable Pending (Hak creator belum siap tarik) Rp 93.340
            new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, creatorUserId, EntryDirection.CREDIT, 93_340),

            // CREDIT: Platform Fee Revenue (Pendapatan platform) Rp 6.000
            new JournalLine(AccountCode.PLATFORM_FEE_REVENUE, null, EntryDirection.CREDIT, 6_000),

            // CREDIT: VAT Payable (Utang PPN) Rp 660
            new JournalLine(AccountCode.VAT_PAYABLE, null, EntryDirection.CREDIT, 660)
        );

        PostJournalResult result = ledgerApi.postJournal(
            idemKey,
            JournalReferenceType.PAYMENT,
            paymentId,
            "Payment " + paymentId + " paid via Midtrans",
            paidAt,
            lines,
            null
        );

        log.info("Journal posted. ID: {}, NewlyCreated: {}", result.journalId(), result.newlyCreated());
    }
}
```

### Contoh 2: Pelepasan Dana Creator (Settlement Release)
Saat settlement dari PG terkonfirmasi, ubah hak creator dari **Pending** menjadi **Available**:

```java
public void releaseCreatorFunds(String settlementId, String creatorUserId, long amount, Instant settledAt) {
    String idemKey = "SETTLEMENT:" + settlementId + ":RELEASE:" + creatorUserId;

    List<JournalLine> lines = List.of(
        // DEBIT: Creator Payable Pending (Kurangi pending)
        new JournalLine(AccountCode.CREATOR_PAYABLE_PENDING, creatorUserId, EntryDirection.DEBIT, amount),

        // CREDIT: Creator Payable Available (Tambah saldo siap tarik)
        new JournalLine(AccountCode.CREATOR_PAYABLE_AVAILABLE, creatorUserId, EntryDirection.CREDIT, amount)
    );

    ledgerApi.postJournal(
        idemKey,
        JournalReferenceType.SETTLEMENT,
        settlementId,
        "Release funds for settlement " + settlementId,
        settledAt,
        lines,
        null
    );
}
```

### Contoh 3: Cek Saldo Siap Tarik Creator
```java
public long getCreatorAvailableBalance(String creatorUserId) {
    return ledgerApi.getBalanceAmount(AccountCode.CREATOR_PAYABLE_AVAILABLE, creatorUserId);
}
```

---

## 6. Diagram Alur & Pembagian Modul

```
 ┌─────────────────────────────────────────────────────────────┐
 │                    MODULE PAYMENT                           │
 │ - Menangani Webhook Midtrans / Flip / Bank                  │
 │ - Mengolah Status Payment, Settlement, Withdrawal           │
 │ - Menghitung Fee & Tax Rate Card                            │
 │ - Membentuk Idempotency Key (e.g. PAYMENT:PAY-1:PAID)      │
 └──────────────────────────────┬──────────────────────────────┘
                                │ calls LedgerApi (Synchronous)
                                ▼
 ┌─────────────────────────────────────────────────────────────┐
 │                    MODULE LEDGER                            │
 │ - Validasi Double-Entry (ΣDEBIT = ΣCREDIT)                  │
 │ - Lazy Get/Create Accounts                                  │
 │ - Batch Lock Accounts in Sorted ID Order (Deadlock-Free)    │
 │ - Record Journal & Entries (Append-Only)                    │
 │ - Update Balance & Optimistic Version                       │
 └─────────────────────────────────────────────────────────────┘
```
