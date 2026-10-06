# GePay — Panduan Dokumentasi

> **Mulai dari sini.** Dokumen ini menjelaskan seluruh sistem dengan bahasa manusia,
> bukan bahasa akuntansi. Kalau kamu bukan akuntan, ini urutan yang benar.
>
> Dokumen konvensi teknis (normatif) tetap [`../AGENTS.md`](../AGENTS.md).

---

## 1. GePay dalam satu menit

GePay adalah backend untuk **donasi/pembelian konten**: seseorang (donatur) membayar
ke platform, platform memotong fee-nya, lalu sisa uang **menjadi hak creator** yang
nantinya bisa **ditarik (withdraw)** ke rekening bank creator.

Uang tidak "langsung" pindah. Ia melewati beberapa tempat:

```
Donatur → [Payment Gateway, mis. Midtrans] → [Rekening bank platform] → [Payout provider, mis. Flip] → rekening Creator
```

Di setiap perpindahan, kita mencatat "siapa berutang/berhak berapa". Pencatatan
itulah yang disebut **ledger (buku besar)**.

Ada beberapa kata yang bikin bingung karena dipakai vendor dan kita dengan arti
berbeda — terutama **"settlement"**. Baca dulu [Glosarium](./glossary.md).

---

## 2. Peta modul (siapa mengerjakan apa)

| Modul | Bahasa manusia | Contoh isi |
|---|---|---|
| `platform` | Fondasi bersama semua modul | format response API, error, i18n, Redis, scheduler |
| `identity` | Siapa pemakainya & apa perannya | login (Firebase), user, role `CREATOR`/`ADMIN`/`SUPER_ADMIN` |
| `ledger` | **Buku besar**: mencatat semua uang | akun, jurnal, saldo creator, piutang ke Midtrans |
| `payment` | **Alur bisnis uang**: donasi, settlement, tarik dana | transaksi Midtrans/Flip, batch settlement, withdrawal, payout |

Aturan besarnya: **`payment` memutuskan & mencatat peristiwanya, tetapi angka uang
hanya boleh ditulis oleh `ledger`.** `payment` memanggil `ledger` dan tidak pernah
menulis jurnal sendiri. `ledger` tidak tahu apa-apa soal Midtrans/Flip (vendor-blind).

```
payment  ──(panggil)──►  ledger
 (kenapa      API          (apa yang berubah
  uangnya                  di buku besar)
  bergerak)
```

---

## 3. Alur uang, dari awal sampai creator pegang uang

Istilah di kotak `[ ]` = akun di ledger (lihat [glossary](./glossary.md) bila perlu).

```
 1. Donatur bayar donasi
    → payment: status PAID, hak creator masih "pending"
    → ledger J-1: hak creator dicatat sebagai [Creator Payable - Pending]

 2. Uang donor sudah diterima PG, tapi belum masuk bank kita
    → [PG Clearing Receivable] = "piutang kita ke Midtrans"

 3. Uang benar-benar masuk rekening bank (bukti: mutasi bank)
    → admin membuat "batch settlement", konfirmasi
    → ledger J-2: [Bank Operating] naik, [PG Clearing Receivable] lunas
    → ledger J-3: hak creator PENDING → AVAILABLE (boleh ditarik)

 4. Platform top-up saldo payout provider (bank → Flip)
    → ledger J-4: [Payout Provider Float] naik, [Bank Operating] turun
      (hanya memindahkan uang kita sendiri, bukan biaya)

 5. Creator minta tarik dana
    → ledger J-5: [Creator Payable - Available] → [Withdrawal Payable] (uang "ditahan")
    → payment: withdrawal REQUESTED

 6. Payout provider (Flip) mengirim uang ke rekening creator
    → ledger J-6: [Withdrawal Payable] lunas, fee payout dicatat, [Payout Provider Float] turun
    → payment: withdrawal PAID
```

Angka & jurnal lengkapnya ada di [`payment/payment-example.md`](./payment/payment-example.md)
dan [`ledger/ledger-example.md`](./ledger/ledger-example.md).

> **Perhatian**: langkah 2 dan 3 di atas sama-sama disebut "settlement" oleh Midtrans
> vs oleh kita. Itu sebabnya alur ini sering terasa membingungkan. Penjelasan
> lengkap: [Glosarium → "Settlement"](./glossary.md#settlement-punya-3-arti).

---

## 4. Kata-kata yang paling sering bikin bingung

| Kata | Arti singkat | Detail |
|---|---|---|
| **Settlement** | punya **3 arti berbeda** (status PG, withdrawable di PG, dan uang masuk bank) | [glossary](./glossary.md#settlement-punya-3-arti) |
| **Pending vs Available** | `pending` = hak creator tapi belum boleh ditarik; `available` = sudah boleh ditarik | [glossary](./glossary.md#pending--available--hold) |
| **Debit / Kredit** | dua sisi catatan; **bukan** "tambah/kurang" | [glossary](./glossary.md#debit--kredit) |
| **Aset / Liabilitas / Pendapatan / Beban** | jenis akun; menentukan saldo tumbuh ke arah mana | [glossary](./glossary.md#jenis-akun) |
| **Payin / Payout** | uang masuk (donasi) / uang keluar (tarik dana) | [glossary](./glossary.md#payin--payout) |
| **T+n hari kerja** | perkiraan kapan dana PG menjadi bisa ditarik | [glossary](./glossary.md#tn-hari-kerja) |

---

## 5. Urutan baca yang disarankan

1. **[Glosarium](./glossary.md)** — semua istilah, wajib kalau bukan akuntan.
2. **[Peta state `payment`](./payment/states.md)** — semua status & perpindahannya.
3. **[Desain `payment`](./payment/payment-design.md)** — aturan bisnis pembayaran.
4. **[Contoh alur uang](./payment/payment-example.md)** — 1 contoh lengkap dengan angka & jurnal.
5. **[Settlement manual (admin)](./payment/manual-settlement.md)** — operasional harian.
6. **[Rencana kerja `payment`](./payment/todo.md)** — apa yang sudah/belum dibuat.
7. Ledger (buku besar):
   - **[Konsep ledger](./ledger/ledger_overview.md)**
   - **[Contoh jurnal](./ledger/ledger-example.md)**
   - **[Kontrak API ledger](./ledger/ledger-design.md)**
   - **[Implementasi ledger (as-built)](./ledger/ledger-implementation.md)**

---

## 6. Status implementasi (per dokumen ini)

| Modul | Status | Keterangan |
|---|---|---|
| `platform` | ✅ stabil | response envelope, error, i18n, Redis, Quartz |
| `identity` | ✅ stabil | Firebase auth, user, role |
| `ledger` | ✅ stabil | double-entry, lazy account, saldo, jurnal; **sengaja tanpa cache** |
| `payment` | 🟡 berjalan | baru skema (`V5`) + entity/enum + `BusinessDayCalculator`; service/controller/adapter belum |

Detail pekerjaan `payment`: [`payment/todo.md`](./payment/todo.md).

---

## 7. Konvensi singkat (kalau bingung kenapa kode begini)

Semua aturan main ada di [`../AGENTS.md`](../AGENTS.md). Tiga yang paling sering
muncul di dokumen ini:

- Semua response HTTP dibungkus `{message, data}` / `{code, message, errors}`.
- `@Transactional` hanya di service, bukan controller/repository.
- Modul `payment` hanya boleh memakai API publik `ledger` (`LedgerApi`), bukan
  isinya.
