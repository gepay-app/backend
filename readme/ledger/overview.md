# Ledger — Konsep & Cara Kerja

> Ledger adalah "buku besar": tempat sistem mencatat **siapa berutang/berhak berapa**
> setiap kali uang bergerak. Semua angka uang di platform **hanya** ditulis di sini.
> Modul lain (mis. `payment`) cuma bilang "uang bergerak", ledger yang mencatatnya.
>
> Kalau istilah akuntansi terasa asing, buka dulu [Glosarium](../glossary.md).
>
> Urutan dokumen ledger:
> 1. **konsep & cara kerja** — dokumen ini
> 2. [Daftar akun](./chart-of-accounts.md) — semua akun + penjelasan
> 3. [Contoh jurnal](./examples.md) — angka nyata per kejadian
> 4. [Implementasi (as-built)](./implementation.md) — isi kode

---

## 1. Aturan dasar

1. **Berpasangan (double-entry).** Satu kejadian = satu jurnal berisi minimal 2 baris.
   Total **debit harus sama dengan total kredit**. Kalau tidak, jurnal ditolak.
2. **Tidak bisa diubah (append-only).** Jurnal hanya bisa ditambah; tidak ada edit/hapus.
   Salah catat diperbaiki dengan **jurnal pembalikan (reversal)** baru yang menunjuk
   jurnal asal — riwayat tetap utuh.
3. **Akun dibuat otomatis.** Akun umum (mis. pendapatan) disiapkan sejak awal. Akun
   per-pemilik (mis. saldo creator) dibuat saat pertama kali dipakai.
4. **Anti-deadlock.** Kalau satu jurnal menyentuh banyak akun, ledger mengunci akun
   berurutan berdasarkan ID, jadi dua transaksi tidak saling mengunci selamanya.

Istilah singkat:

- **Debit / Kredit** = dua kolom buku, **bukan** "tambah/kurang".
- **Jurnal** = satu kejadian, punya `idempotency_key` unik.
- **Entry** = satu baris debit/kredit di dalam jurnal.
- **Owner** = pemilik akun (user, provider, bank). Akun global tidak punya owner.

---

## 2. API: `LedgerApi`

Modul lain hanya boleh memakai `com.gepe.gepay.ledger.api.LedgerApi`:

```java
public interface LedgerApi {
    // Akun (lazy: buat otomatis saat pertama dipakai)
    AccountDto getOrCreateAccount(AccountCode code, String ownerRef);
    AccountDto getAccount(AccountCode code, String ownerRef);

    // Tulis jurnal (satu-satunya jalur tulis)
    PostJournalResult postJournal(
            String idempotencyKey,          // wajib, dari pemanggil
            JournalReferenceType referenceType,
            String referenceId,
            String description,
            Instant occurredAt,
            List<JournalLine> lines,        // minimal 2 baris, Σdebit = Σkredit
            Long reversesJournalId          // null, kecuali jurnal pembalikan
    );

    // Baca saldo (selalu dari DB, tidak di-cache)
    AccountDto getBalance(AccountCode code, String ownerRef);
    long getBalanceAmount(AccountCode code, String ownerRef);

    // Saldo user terkomposisi (pending / available / hold) untuk ownerRef
    BalanceResponse getUserBalance(String ownerRef);
}
```

`JournalLine` = `(AccountCode, ownerRef, DEBIT|CREDIT, amount)`.

> **Endpoint saldo milik ledger.** `GET /api/v1/balance` di-serve
> `ledger/internal/delivery/http/BalanceController`, yang meng-resolve current user
> lewat `identity.api.CurrentUser` lalu memanggil `getUserBalance(ownerRef)`. Karena
> itu `ledger` mendeklarasikan `allowedDependencies = "identity::api"` (satu-satunya
> dependency lintas-modul ledger).

### Idempotency key

Dibuat **pemanggil** (bukan ledger), format `{TIPE}:{ID}:{AKSI}`:

| Kejadian | Key |
|---|---|
| Pembayaran dibayar | `PAYMENT:{paymentId}:PAID` |
| Settlement dikonfirmasi | `SETTLEMENT:{settlementId}:CONFIRMED` |
| Release hak creator | `SETTLEMENT:{settlementId}:RELEASE:{creatorUserId}` |
| Withdrawal hold | `WITHDRAWAL:{withdrawalId}:HOLD` |
| Payout selesai/gagal | `PAYOUT:{payoutId}:COMPLETED` / `:FAILED` |
| Fund transfer | `FUND_TRANSFER:{fundTransferId}` |
| Refund | `REFUND:{refundId}` |
| Adjustment | `ADJUSTMENT:{adjustmentId}` |

> Kenapa key dibuat pemanggil? Hanya pemanggil yang tahu ID unik event bisnis. Kalau
> ledger yang membuat key (mis. dari hash deskripsi/timestamp), retry bisa menghasilkan
> key beda → jurnal dobel.

### Error

`LedgerError implements ErrorCode` (kode i18n `ledger.*`):

| Error | HTTP |
|---|---|
| `ACCOUNT_NOT_FOUND` | 404 |
| `UNBALANCED_JOURNAL` | 400 |
| `IDEMPOTENCY_CONFLICT` | 409 |
| `JOURNAL_MIN_LINES` / `INVALID_AMOUNT` | 400 |
| `REVERSES_JOURNAL_NOT_FOUND` | 404 |
| `JOURNAL_ALREADY_REVERSED` | 409 |
| `ACCOUNT_INACTIVE` / `CONCURRENT_BALANCE_UPDATE` | 409 |
| `ACCOUNT_CODE_INVALID` / `OWNER_REF_REQUIRED` | 400 |

---

## 3. Konfigurasi modul

- **Package**: `com.gepe.gepay.ledger` (modul CLOSED, `id="ledger"`).
- **API**: `com.gepe.gepay.ledger.api` (`@NamedInterface("api")`).
- **Cache**: sengaja **tidak** ada — saldo selalu dibaca dari DB agar tidak basi.
- **Migrasi**: `V4__ledger_tables.sql` (schema + seed akun global & `BANK-1`).
- **i18n**: `src/main/resources/i18n/ledger/messages*.properties` (prefix `ledger.`).

---

## 4. Hubungan dengan modul `payment`

`payment` **tidak menulis jurnal sendiri**. Ia menyusun daftar `JournalLine` +
`idempotencyKey`, lalu memanggil `LedgerApi.postJournal(...)`. Tabel lengkap event →
key → jurnal ada di [Payment §5](../payment/overview.md).
