# GePay

Backend **GePay** — platform donasi/pembelian konten dengan pencatatan keuangan
double-entry. Dibangun sebagai *modular monolith* (satu aplikasi deploy) berbasis
**Spring Boot 4 + Spring Modulith**, di mana batas antar-modul **ditegakkan mesin**
(`ModularityTests`), bukan sekadar konvensi.

> **Baru di sini?** Baca dulu **[`readme/README.md`](readme/README.md)** — pintu masuk
> yang menjelaskan sistem dengan bahasa manusia (plus
> [`readme/glossary.md`](readme/glossary.md) kalau bukan akuntan).
> Dokumen konvensi normatif: **[`AGENTS.md`](AGENTS.md)**.

---

## Modul

| Modul | Peran (manusia) | Status |
|---|---|---|
| `platform` | Fondasi bersama: response envelope, error, i18n, Redis, Quartz | ✅ stabil |
| `identity` | Login (Firebase), user, role | ✅ stabil |
| `ledger` | Buku besar double-entry: akun, jurnal, saldo | ✅ stabil |
| `payment` | Mesin pembayaran: payin, settlement, withdrawal, payout | 🟡 berjalan |

Aturan besar: `payment` memutuskan **kapan** uang bergerak, tetapi **angka** hanya
ditulis oleh `ledger` (lewat `LedgerApi`). `ledger` bersifat vendor-blind.

Detail: [`readme/README.md`](readme/README.md).

## Stack

| Bagian | Pilihan |
|---|---|
| Java / Boot / Modulith | 25 / 4.1.1 / 2.1.1 |
| Build | Maven wrapper |
| Database | PostgreSQL (Flyway) |
| Secondary store | Redis |
| Scheduler | Quartz (JDBC, clustered) |
| Verifikasi batas modul | `ModularityTests` (`modules.verify()`) |

## Menjalankan

Prasyarat: PostgreSQL & Redis yang bisa diakses (repo ini **tidak** berisi
`compose.yaml`; infrastruktur dikelola di luar repo — lihat `AGENTS.md` §5).
Default lokal: `localhost:5432` db `gepay` user/password `root`/`root`, dan
`localhost:6379` user/password `root`/`root`. Semua bisa di-override via env
`SPRING_*` (contoh di `.env`, jangan commit secret).

```bash
./mvnw spring-boot:run     # butuh Postgres & Redis jalan
./mvnw test                # unit + modularity + slice/integration
./mvnw verify              # semuanya, termasuk packaging
```

> **Catatan Flyway**: migrasi `payment` masih tahap rancangan. Kalau kamu mengubah
> file migrasi yang sudah pernah dijalankan (`V1__`..`V5__`), checksum akan bentrok.
> Bersihkan dulu DB (`flyway -configFiles=flyway.conf clean`, atau drop & create
> ulang database `gepay`) sebelum menjalankan ulang.

## Multi-instance

Aplikasi dirancang aman berjalan beberapa instance pada Postgres/Redis yang sama:

- **Quartz clustered**: trigger dijalankan satu instance saja.
- **Graceful shutdown**: SIGTERM → tuntaskan request in-flight, lalu tutup context.
- **Event Modulith at-least-once**: ditulis ke `event_publication`; listener wajib idempotent.
- **Instance stateless**: ID UUID v7 di aplikasi; cache memakai Redis bersama.
- **Dilarang**: `@Scheduled` (pakai Quartz), cache lokal, state di memory antar-request.

Detail & aturan: `AGENTS.md` §11.

## Menambah modul baru

1. Paket `com.gepe.gepay.<modul>` + `package-info.java` (CLOSED, `allowedDependencies`).
2. `api/` (`@NamedInterface("api")`): facade + DTO/event record.
3. `internal/`: `entity`, `repository`, `service`, `delivery/http`, `exception/<Modul>Error`, `listener`.
4. i18n `src/main/resources/i18n/<modul>/messages.properties` (+ `_id`), migrasi Flyway bila ada tabel baru.
5. `./mvnw test` — `ModularityTests` wajib hijau.

Pola & konvensi lengkap: `AGENTS.md` §2, §9. Contoh modul: `identity`, `ledger`.
