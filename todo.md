# GePay — Rencana Implementasi Modul `donation` & Overlay

> **Dokumen ini adalah instruksi kerja untuk AI/engineer.**
> Baca dulu [`AGENTS.md`](./AGENTS.md) dan semua file di [`docs/agents/`](./docs/agents/).
> Kalau ada konflik antara dokumen ini dan `AGENTS.md`, **`AGENTS.md` menang** —
> dokumen ini hanya mengatur *apa* yang dibangun, bukan mengubah konvensi.
>
> Status: **RENCANA (belum dieksekusi)**. Implementasi bertahap per Fase di §10.
> Setiap Fase harus hijau (`./mvnw test`, termasuk `ModularityTests`) sebelum lanjut.

---

## 1. Tujuan & lingkup

Core bisnis: **ledger → payment → donation**. Semua di satu modular monolith.

Yang dibangun di sini:

1. **Domain event dari `payment`** saat sebuah payment `PAID` (agar consumer bisa bereaksi tanpa polling).
2. **Modul `donation`**: creator punya *donation page* publik + *overlay key*; donor membuat donasi (text atau video YouTube) yang diproses lewat `payment`.
3. **Overlay queue yang dikelola backend**: donasi `PAID` masuk antrean per creator; ditampilkan di OBS via WebSocket; status `PENDING → PLAYING → PLAYED`, bisa `SKIPPED`/`FAILED`, bisa di-retry.
4. **Halaman pembayaran realtime** (SSE) untuk donor — boleh hilang saat koneksi putus.

### Non-goals (JANGAN dikerjakan sekarang)

- KYC, verifikasi identitas, approval creator. Registrasi → langsung bisa terima donasi.
- Refund / chargeback / reconciliation / adjustment (Milestone 5 payment).
- Payout webhook Flip, bank-account inquiry, provider payout selain yang sudah ada.
- Donasi tipe selain **TEXT** dan **YOUTUBE**.
- Frontend (dikerjakan terpisah oleh user). Dokumen ini hanya mengatur backend + kontraknya.
- Analitik/metrics lanjutan, laporan.

### Prinsip yang dipegang (ringkas, dari `AGENTS.md`)

- `api` = kontrak murni (interface, DTO record, event record, enum). Tidak boleh Spring Data/JPA/Web.
- Akses antar-modul hanya lewat `<module>.api`, dideklarasikan di `allowedDependencies`.
- `@Transactional` hanya di service/facade. Controller & repository **tidak** `@Transactional`.
- `platform` never depends on feature module; `donation` **tidak** boleh diakses `payment`/`ledger`.
- Error: `throw new ServiceException(DonationError.X, args…)`; `code` = message key = public API.
- Enum disimpan sebagai `varchar`; tidak ada DB enum type.
- Cross-module reference hanya simpan FK id (tanpa `@ManyToOne` lintas modul).
- Caching lewat Redis (`CacheSpec`), read path return DTO api, evict saat mutasi.
- **Tanpa `@Scheduled`** — Quartz clustered saja.
- Event Modulith = record di `<module>.api`, publish **setelah commit**, listener **idempotent** (at-least-once).
- Entity: audit fields manual, tanpa `BaseEntity`; write lewat `*Id`, read lewat `@ManyToOne` read-only.
- Jangan commit secret. Semua bisa di-override `SPRING_*`/env.

---

## 2. Alur besar (end-to-end)

```
[Donor] buat donasi (email, amount, tipe: text|video, message|youtubeUrl, channel)
   │  POST /api/v1/donations
   ▼
[donation] simpan Donation(status=PENDING) + panggil payment::api createPayment(type="DONATION")
   │            └─ payment buat Payment + PaymentAttempt(INITIATED→PENDING), charge ke PG
   ▼
[donation] balas { donationId, paymentId, channel, instruction (no VA/QR), expiresAt }
   │  frontend redirect ke halaman pembayaran + buka SSE status
   ▼
[Donor] bayar di VA/QR
   ▼
[PG] POST /api/v1/webhooks/{provider}
   │  WebhookController: verifikasi signature → XADD ke Redis stream "payment:webhook:stream" → 200 OK cepat
   ▼
[payment worker] WebhookStreamConsumer (consumer group "gepay-payment-webhooks")
   │  PaymentWebhookService.processIncomingWebhook  @Transactional
   │    ├─ inbox idempotency (processed_events)
   │    ├─ post J-1 ke ledger
   │    ├─ markPaid
   │    └─ publish PaymentPaidEvent  (AFTER_COMMIT)
   ▼
[donation listener] @ApplicationModuleListener  (idempotent)
   │    ├─ Donation: PENDING → PAID (set durationSeconds)
   │    ├─ buat OverlayEvent(status=PENDING) untuk creator terkait
   │    └─ OverlayDispatcher.dispatch(creatorId)
   ▼
[overlay dispatcher] (backend-managed queue)
   │    ├─ paused?           → stop
   │    ├─ ada PLAYING?      → stop (tunggu ack)
   │    ├─ display offline?  → stop (biarkan PENDING, durable)
   │    └─ claim PENDING tertua → PLAYING → publish ke Redis `donation:overlay:play:{creatorId}`
   ▼
[overlay display WS] /ws/overlay/display?key=xxxx   (OBS browser source, PASIF)
   │    terima play → animasi (video youtube / text) → selesai → kirim ack
   ▼
[overlay dispatcher] PLAYING → PLAYED → dispatch berikutnya
```

**Kontrol (halaman lain, interaktif):** `/ws/overlay/control` (auth Firebase) — `pause`, `resume`,
`skip`, `retry`, `retryAll`, `purge`; menerima state queue secara realtime.

---

## 3. Aturan bisnis (WAJIB tepat)

### 3.1 Hanya `PAID` yang masuk overlay

`OverlayEvent` hanya dibuat dari `Donation` yang sudah `PAID` (dari `PaymentPaidEvent`).
`PENDING`/`EXPIRED`/`FAILED` **tidak** masuk antrean.

### 3.2 Tipe donasi

`DonationType { TEXT, YOUTUBE }`.

- **TEXT** — donor menulis `message` (maks **350 karakter**).
- **YOUTUBE** — donor memberi URL YouTube. **Hanya YouTube** yang valid (lihat §3.4).
  (Nama `YOUTUBE`, bukan `VIDEO`, supaya nanti bisa ada fitur upload video tanpa rename tipe.)

### 3.3 Durasi tampil

Durasi dihitung **backend**, disimpan di `overlay_events.duration_seconds`, dan dikirim ke
display. Display berhenti saat durasi tercapai **atau** video selesai (mana dulu), lalu ack.

**YOUTUBE** — laju 500 rupiah/detik, cap 30 menit, lantai 10 detik:

```
ratePerSecond = 500          # config: donation.overlay.video.rate-per-second
requested     = floor(amount / ratePerSecond)
capped        = min(requested, 1800)      # config: video.max-seconds = 1800 (30 menit)
duration      = max(capped, 10)           # config: video.min-seconds = 10
```

- Jika **durasi video asli < `duration`** → putar penuh lalu ack (frontend yang tahu durasi asli video).
- Contoh: `amount=5_000` → 10s. `amount=100_000` → 200s. `amount=1_000_000` → 1800s (cap).
- Rekomendasi: channel `minAmount` untuk YOUTUBE sebaiknya ≥ 5_000 agar minimal 10 detik "berbayar".
  (Batas channel sudah divalidasi `payment`; donation tidak perlu validasi ulang.)

**TEXT** — berbasis jumlah karakter & kecepatan baca manusia (bukan amount):

```
charCount  = length(message)               # maks 350, config: text.max-characters
cps        = 12                            # config: text.chars-per-second
raw        = ceil(charCount / cps)
duration   = clamp(raw, 10, 60)            # config: text.min-seconds=10, text.max-seconds=60
```

- 350 karakter → `ceil(350/12)=30`s. Jauh di bawah 30 menit. Amount **tidak** menambah durasi text.
- Kalau `message` kosong untuk TEXT → tolak (field-level, lihat §3.5).

### 3.4 Validasi URL YouTube

Terima hanya host YouTube, ekstrak 11-char video id `[A-Za-z0-9_-]{11}`:

- `youtube.com/watch?v=<id>`
- `youtu.be/<id>`
- `youtube.com/embed/<id>`
- `youtube.com/shorts/<id>`
- `m.youtube.com/...`, `www.youtube.com/...`, `music.youtube.com/...` (opsional)

Host lain → tolak (field `videoUrl`, lihat §3.5). Simpan `video_id` + URL kanonik
`https://www.youtube.com/watch?v=<id>`. **Jangan** panggil API YouTube (durasi asli = urusan frontend).
Regex id: `^[A-Za-z0-9_-]{11}$`. Untuk YOUTUBE, `videoUrl` wajib; `message` opsional (caption, tetap ≤350 bila ada).

### 3.5 Semantik validasi (field + message)

Semua validasi **domain/kombinasi field** dikerjakan di **service**, bukan di controller,
via method `private` kecil (mis. `validateTypeFields(cmd)`). Hasilnya **selalu**
`platform.exception.ValidationException(list)` dengan `ValidationError(field, message)`
(message sudah dilokalkan lewat `MessageHelper`). Ini memakai envelope error yang sama
dengan bean validation (`ErrorResponse.code = "validation.failed"`, ada `errors[]`),
jadi **frontend tidak parsing dua kali** — cukup baca `errors[].field` + `errors[].message`.

Aturan yang divalidasi di service:

| Kondisi | `field` | Message key |
|---|---|---|
| `type=TEXT` tapi `message` kosong/blank | `message` | `donation.message_required` |
| `type=TEXT` tapi `videoUrl` terisi | `videoUrl` | `donation.video_not_allowed_for_text` |
| `type=YOUTUBE` tapi `videoUrl` kosong | `videoUrl` | `donation.video_required` |
| `type=YOUTUBE` dan URL bukan YouTube / id tidak valid | `videoUrl` | `donation.invalid_youtube_url` |
| `message` > 350 karakter (semua tipe) | `message` | `donation.message_too_long` |
| `type` bukan salah satu enum | `type` | `donation.invalid_type` |

Bean validation di request DTO tetap dipakai untuk hal elementer (`@NotNull`, `@Min`,
`@Email`, `@Size`) supaya 400 cepat tanpa masuk service; sisanya di service.

### 3.6 Anonim

`is_anonymous` opsional. Jika true, display menampilkan nama "Anonim" (payload sudah disanitasi backend).

---

## 4. Data model

Semua di schema baru `donation`. Migrasi: `V6__donation_tables.sql` (Flyway, satu file untuk modul).
Ikuti §3 persistence: BigDecimal dilarang untuk uang — uang = `bigint` (rupiah bulat). UUID v7 untuk id.

### 4.1 `donation.donation_pages`

**Satu creator = satu page** (1:1). Tujuan (penerima) donasi = **`creator_id`** = `userId`
(UUID) langsung dari `CurrentUser.userId()` — **tidak perlu `authId`, tidak ada perubahan `identity`**.
Overlay OBS diakses lewat **`overlay_key` rahasia** (acak, bukan userId), mis. `/overlay?key=xxxx`.

| Kolom | Tipe | Catatan |
|---|---|---|
| `id` | uuid PK | v7 |
| `creator_id` | uuid NOT NULL **UNIQUE** | userId (`identity.users.id`); tujuan donasi + untuk `payment` |
| `overlay_key` | varchar(64) NOT NULL **UNIQUE** | **rahasia**, acak (mis. 32 byte base62); key display OBS |
| `display_name` | varchar(120) | nama tampil di overlay/page |
| `title` | varchar(200) | judul halaman |
| `description` | text | deskripsi |
| `is_active` | boolean NOT NULL DEFAULT true | |
| `created_at`, `updated_at` | timestamptz NOT NULL | |

Auto-provision: `getOrCreateForCurrentUser()` saat creator pertama buka dashboard/overlay —
`creator_id = CurrentUser.userId()`, `overlay_key` di-generate (SecureRandom) & harus unik.

> **Keamanan:** `overlay_key` **rahasia** — siapa punya key bisa **melihat** overlay (display),
> jadi jangan di-log/jangan expose di endpoint publik. **Kontrol tetap butuh auth Firebase +
> ownership** (`CurrentUser.userId() == page.creator_id`). Endpoint `GET /page` (milik sendiri)
> boleh mengembalikan `overlay_key`; endpoint publik **tidak**.

**Rotasi `overlay_key` (creator bisa regenerate):**

- Endpoint owner: `POST /api/v1/donations/page/overlay-key/rotate` → generate key baru unik,
  simpan, balas `{ "overlayKey": "<baru>" }`.
- Tujuan: kalau key lama bocor, creator menggantinya agar orang lain tak bisa lagi melihat overlay.
- Efek: **key lama langsung tidak valid** (handshake baru ditolak). Semua sesi **display**
  milik creator itu ditutup (broadcast Redis `donation:overlay:key-rotated:{creatorId}` → tiap node
  menutup sesi display lokal creator tsb). OBS harus update URL & reconnect manual.
- Antrean & status **tidak** terpengaruh (di-key `creator_id`, bukan key). Control page menerima
  key baru lewat pesan WS `keyRotated` (lihat §7.3).
- Rotate **bukan** idempoten (selalu key baru). Rate-limit sederhana opsional.
- Format key: `SecureRandom` → base62, panjang ≥ 32 (kolom `varchar(64)`). Simpan **plaintext**
  (harus bisa ditampilkan lagi ke owner); hashing bisa ditambah nanti bila perlu.

### 4.2 `donation.donations`

| Kolom | Tipe | Catatan |
|---|---|---|
| `id` | uuid PK | v7 |
| `page_id` | uuid NOT NULL | FK ke `donation_pages.id` (intra-modul) |
| `creator_id` | uuid NOT NULL | tujuan donasi (userId); denormalisasi untuk query antrean |
| `idempotency_key` | varchar(160) NOT NULL **UNIQUE** | idempotensi pembuatan donasi |
| `payment_id` | uuid | FK logis ke `payment.payments.id` (TEXT/YOUTUBE) |
| `channel_code` | varchar(40) NOT NULL | channel yang dipilih donor |
| `payment_reference_number` | varchar(255) | instruksi bayar (no VA / QR), snapshot dari payment |
| `payment_expires_at` | timestamptz | kedaluwarsa pembayaran |
| `total_charged_amount` | bigint | total tagihan ke donor (termasuk fee PG) |
| `donor_name` | varchar(120) | |
| `donor_email` | varchar(200) | |
| `amount` | bigint NOT NULL | rupiah |
| `type` | varchar(20) NOT NULL | `TEXT` / `YOUTUBE` |
| `message` | varchar(350) | wajib TEXT, opsional caption YOUTUBE |
| `video_id` | varchar(20) | wajib YOUTUBE |
| `is_anonymous` | boolean NOT NULL DEFAULT false | |
| `status` | varchar(20) NOT NULL | `PENDING`/`PAID`/`EXPIRED`/`FAILED` |
| `duration_seconds` | int | diisi saat PAID |
| `created_at` | timestamptz NOT NULL | |
| `paid_at` | timestamptz | |
| `updated_at` | timestamptz NOT NULL | |

Index: `(creator_id, status, created_at, id)`, partial unique `(payment_id) WHERE payment_id IS NOT NULL`.

### 4.3 `donation.overlay_events` (queue durable)

| Kolom | Tipe | Catatan |
|---|---|---|
| `id` | uuid PK | v7 (time-ordered) |
| `donation_id` | uuid NOT NULL **UNIQUE** | idempotency: 1 donasi = 1 overlay event |
| `creator_id` | uuid NOT NULL | tujuan donasi (userId) = pemilik antrean |
| `type` | varchar(20) NOT NULL | `TEXT` / `YOUTUBE` |
| `status` | varchar(20) NOT NULL | lihat §5 |
| `payload` | jsonb NOT NULL | data siap kirim ke display (lihat §7.2) |
| `duration_seconds` | int NOT NULL | §3.3 |
| `attempts` | int NOT NULL DEFAULT 0 | jumlah percobaan dispatch |
| `created_at` | timestamptz NOT NULL | urutan FIFO: `ORDER BY created_at, id` |
| `dispatched_at` | timestamptz | kapan terakhir dikirim (watchdog) |
| `played_at` | timestamptz | kapan ack diterima |
| `updated_at` | timestamptz NOT NULL | |

Index: `(creator_id, status, created_at)`.

> `payload` menyimpan `donorName`, `isAnonymous`, `amount`, `message`, `videoId`,
> `canonicalUrl`, `durationSeconds`. Dokumen ini mendefinisikan bentuknya di §7.2.

### 4.4 Relasi lintas modul

`donation` **hanya** menyimpan `payment_id` (uuid). **Tidak ada** `@ManyToOne` ke `payment`.
`donation` **tidak** mengakses schema/tabel `payment.*` secara langsung — hanya lewat `payment::api`.
FK antar-modul (`creator_id → identity.users`, `payment_id → payment.payments`) **tidak** dipasang
di DB (decoupling); cukup index.

### 4.5 DDL referensi — `V6__donation_tables.sql`

> Gaya mengikuti `V5__payment_tables.sql` (schema per modul, `ux_`/`ix_`/`ck_`, enum = varchar
> + CHECK, timestamp = `timestamptz`, `@UpdateTimestamp` di entity yang menjaga `updated_at`).

```sql
CREATE SCHEMA IF NOT EXISTS donation;

-- =====================================================================
-- DONATION — halaman creator & donasi
-- =====================================================================

-- Satu creator = satu halaman donasi (1:1). creator_id = userId (identity.users.id),
-- disimpan sebagai UUID tanpa FK lintas modul (AGENTS.md §3 persistence).
-- overlay_key = rahasia untuk display OBS; BISA DIROTASI creator (regenerate).
CREATE TABLE donation.donation_pages
(
    id           UUID         PRIMARY KEY,
    creator_id   UUID         NOT NULL,
    overlay_key  VARCHAR(64)  NOT NULL,
    display_name VARCHAR(120),
    title        VARCHAR(200),
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_donation_pages_creator     UNIQUE (creator_id),
    CONSTRAINT ux_donation_pages_overlay_key UNIQUE (overlay_key)
);

-- Satu baris = satu donasi. Tujuan/penerima = creator_id. payment_id = referensi
-- lintas modul ke payment.payments.id (UUID, tanpa FK cross-schema).
-- ck_donations_payload menegakkan aturan tipe: TEXT -> message wajib & video_id kosong;
-- YOUTUBE -> video_id wajib (message opsional sebagai caption).
CREATE TABLE donation.donations
(
    id                       UUID         PRIMARY KEY,
    page_id                  UUID         NOT NULL REFERENCES donation.donation_pages (id),
    creator_id               UUID         NOT NULL,
    idempotency_key          VARCHAR(160) NOT NULL,
    payment_id               UUID,
    channel_code             VARCHAR(40)  NOT NULL,
    payment_reference_number VARCHAR(255),
    payment_expires_at       TIMESTAMPTZ,
    total_charged_amount     BIGINT       CHECK (total_charged_amount IS NULL OR total_charged_amount > 0),
    donor_name               VARCHAR(120),
    donor_email              VARCHAR(200),
    amount                   BIGINT       NOT NULL CHECK (amount > 0),
    type                     VARCHAR(20)  NOT NULL,   -- TEXT | YOUTUBE
    message                  VARCHAR(350),
    video_id                 VARCHAR(20),             -- 11-char id YouTube
    is_anonymous             BOOLEAN      NOT NULL DEFAULT FALSE,
    status                   VARCHAR(20)  NOT NULL,   -- PENDING | PAID | EXPIRED | FAILED
    duration_seconds         INTEGER      CHECK (duration_seconds IS NULL OR duration_seconds > 0),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at                  TIMESTAMPTZ,
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_donations_idem    UNIQUE (idempotency_key),
    CONSTRAINT ck_donations_type    CHECK (type IN ('TEXT', 'YOUTUBE')),
    CONSTRAINT ck_donations_status  CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'FAILED')),
    CONSTRAINT ck_donations_payload CHECK (
        (type = 'TEXT'    AND message IS NOT NULL AND video_id IS NULL)
     OR (type = 'YOUTUBE' AND video_id IS NOT NULL)
    )
);

-- Antrean query: ambil donasi creator tertentu; cek status.
CREATE INDEX ix_donations_creator ON donation.donations (creator_id, status, created_at, id);
-- 1 payment = maks 1 donasi (partial: payment_id boleh NULL sebelum payment dibuat).
CREATE UNIQUE INDEX ux_donations_payment ON donation.donations (payment_id) WHERE payment_id IS NOT NULL;

-- Antrean overlay durable, per creator. Satu donasi = maksimal satu overlay event.
-- payload = JSON siap kirim ke display (§7.2). FIFO: ORDER BY created_at, id.
-- 'PENDING' bertahan saat display offline (durable), bukan hanya di Redis.
CREATE TABLE donation.overlay_events
(
    id               UUID         PRIMARY KEY,
    donation_id      UUID         NOT NULL REFERENCES donation.donations (id),
    creator_id       UUID         NOT NULL,
    type             VARCHAR(20)  NOT NULL,   -- TEXT | YOUTUBE
    status           VARCHAR(20)  NOT NULL,   -- PENDING | PLAYING | PLAYED | SKIPPED | FAILED
    payload          JSONB        NOT NULL,
    duration_seconds INTEGER      NOT NULL CHECK (duration_seconds > 0),
    attempts         INTEGER      NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    dispatched_at    TIMESTAMPTZ,             -- kapan terakhir dikirim (watchdog ack-timeout)
    played_at        TIMESTAMPTZ,             -- kapan ack diterima
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_overlay_events_donation UNIQUE (donation_id),  -- idempotency event listener
    CONSTRAINT ck_overlay_events_type     CHECK (type IN ('TEXT', 'YOUTUBE')),
    CONSTRAINT ck_overlay_events_status   CHECK (
        status IN ('PENDING', 'PLAYING', 'PLAYED', 'SKIPPED', 'FAILED')
    )
);

-- Claim FIFO per creator + lookup status/antrean.
CREATE INDEX ix_overlay_events_queue ON donation.overlay_events (creator_id, status, created_at, id);
```

**Catatan skema:**

- `created_at`/`updated_at` diisi aplikasi (`@CreationTimestamp`/`@UpdateTimestamp`); default
  `now()` hanya jaring pengaman.
- ID `UUID` (v7) dari aplikasi (`UuidCreator.getTimeOrderedEpoch()`), bukan `DEFAULT gen_random_uuid()`.
- `overlay_events` **tidak** menyimpan `creator_auth_id`; cukup `creator_id`.
- `dispatched_at`/`played_at` + `attempts` khusus watchdog & retry; `status` sumbu utama state machine.

---

## 5. State machine — `overlay_events`

```
                 (DonationPAID)
                        │
                        ▼
   retry ┌───────────► PENDING ──claim──► PLAYING ──ack──► PLAYED ──retry──┐
         │                                 │                              │
         │                                 │ timeout (watchdog)           │
         │                                 ▼                              │
         │                              FAILED ──retry──────────────────►─┘
         │                                 ▲
         │                                 │
         └──────────── SKIPPED ◄──skip── PLAYING/PENDING
                        (retry)
```

| Status | Arti |
|---|---|
| `PENDING` | di antrean, belum dikirim. Termasuk saat display offline. |
| `PLAYING` | sudah dikirim ke display, menunggu ack. |
| `PLAYED` | display meng-ack selesai. |
| `SKIPPED` | di-skip dari controller (tanpa play). |
| `FAILED` | timeout ack / error. |

Transisi legal:
- `PENDING → PLAYING` (dispatcher claim), `PLAYING → PLAYED` (ack), `PLAYING → FAILED` (watchdog), `PLAYING|PENDING → SKIPPED` (skip).
- `PLAYED|SKIPPED|FAILED → PENDING` (**retry**), reset `dispatched_at=null`, `played_at=null`.
- `PENDING` tidak di-`PLAYING` dua kali (claim atomik, §6.3).

Setiap transisi **hanya** lewat service (`@Transactional`), jangan update via controller.

---

## 6. Komponen backend

Semua di modul `donation` (kecuali §6.7 yang di `payment`).

### 6.1 Modul descriptor — **tanpa `api` publik**

Tidak ada modul lain yang bergantung ke `donation` (lihat §12 keputusan). Jadi `donation`
**tidak punya** paket `api`/named interface. Semua kode di `internal`. Controller/WS handler
(di `internal/delivery`) memanggil service `internal` langsung — legal karena masih satu modul.

`donation/package-info.java`:
```java
@ApplicationModule(id = "donation", allowedDependencies = {"payment::api", "identity::api"})
package com.gepe.gepay.donation;
```

Konsekuensi: DTO request/response & payload WS tidak perlu di `api`; taruh di
`internal/delivery/http/req` (request) dan `internal/delivery/http/res` (response) atau
`internal/dto` (record yang dipakai lintas delivery). Enum domain di `internal/entity`.

### 6.2 Service internal (bukan facade)

`DonationPageService` (provisioning halaman, lookup by `creator_id` & by `overlay_key`,
rotate `overlay_key`),
`DonationService` (buat/lihat donasi; validasi §3.5),
`OverlayQueueService` (transisi status §5),
`OverlayDispatcher` (§6.3). Semua `@Service`, `@Transactional` di method yang menulis.
Dependensi lintas modul: hanya `payment.api.PaymentApi` dan `identity.api.CurrentUser`.
Tidak ada interface publik yang di-expose.

### 6.3 OverlayDispatcher (inti)

Tanggung jawab: memajukan antrean dengan aman lintas instance.

```
dispatch(creatorId):
  if paused(creatorId)            -> return
  if hasPlaying(creatorId)        -> return
  if !presence(creatorId)         -> return            # display offline, biarkan PENDING
  event = claimNextPending(creatorId)                  # atomik, lihat bawah
  if event == null                -> return
  publish Redis "donation:overlay:play:{creatorId}" {id, payload, durationSeconds}
```

`claimNextPending` — native query dengan `FOR UPDATE SKIP LOCKED` agar tidak dobel antar-node:
```sql
SELECT * FROM donation.overlay_events
WHERE creator_id = :creatorId AND status = 'PENDING'
ORDER BY created_at, id
LIMIT 1
FOR UPDATE SKIP LOCKED
```
Lalu set `PLAYING`, `dispatched_at=now()`, `attempts=attempts+1` dalam transaksi yang sama,
**commit sebelum** publish. (Publish Redis gagal → watchdog yang menyelamatkan, §6.5.)

**Trigger dispatch dipanggil dari:** (a) listener `DonationPaid` setelah commit, (b) display connect,
(c) ack diterima, (d) `resume`, (e) `retry`/`retryAll`, (f) watchdog saat mengembalikan item.

### 6.4 Presence & control state (Redis)

| Key | TTL | Isi |
|---|---|---|
| `donation:overlay:presence:{creatorId}` | 30s | heartbeat display (refresh tiap 10s) |
| `donation:overlay:paused:{creatorId}` | – | `1` jika paused |
| `donation:overlay:current:{creatorId}` | – | overlayEventId yang `PLAYING` |

Semua transient; kebenaran tetap di DB. Presence dipakai **hanya** untuk memutuskan dispatch.

### 6.5 Watchdog (Quartz)

- `OverlayAckWatchdogJob` `@DisallowConcurrentExecution`, cron `donation.overlay.watchdog-cron` (default `0/30 * * * * ?`).
- Cari `PLAYING` dengan `dispatched_at < now() - ackTimeout` (default 60s) → `FAILED`, `attempts++`.
- Opsional `auto-requeue` (config `donation.overlay.auto-requeue-on-timeout`, default true) → `PENDING` lalu `dispatch` lagi.
- Aman clustered karena status durable + Quartz clustered.

### 6.6 Realtime & registry

- **Node-local registry** `OverlaySessionRegistry`: `Map<creatorId, Set<WebSocketSession>>` (display) + `Map<creatorId, Set<WebSocketSession>>` (control).
- **Redis pub/sub** untuk fan-out lintas node:
  - `donation:overlay:play:{creatorId}` → semua node; node yang punya sesi display mengirim ke socket.
  - `donation:overlay:control:{creatorId}` → state/queue snapshot ke control.
  - `donation:overlay:key-rotated:{creatorId}` → tiap node menutup sesi **display** lokal
    creator tsb (OBS lama diputus) & memberi tahu control.
- Handshake display: `overlay_key` → lookup page → dapat `creator_id`; sesi didaftarkan by `creator_id`.
- Ack diterima node pemilik display → `OverlayQueueService.markPlayed(id)` (`@Transactional`) → `dispatch` lagi.

### 6.7 Perubahan di modul `payment` (Fase 0)

**Tujuan:** payment menerbitkan event saat `PAID`, agar `donation` tidak polling.

- Baru: `payment/api/event/PaymentPaidEvent.java`
  ```java
  public record PaymentPaidEvent(
      UUID paymentId, String type, UUID userId,
      Long grossAmount, Long netCreatorAmount,
      Map<String, Object> metadata, Instant paidAt
  ) {}
  ```
  `payment/api/event/package-info.java` → `@NamedInterface("api")`.
- Ubah `payment/internal/service/PaymentWebhookService.java`: inject `ApplicationEventPublisher`
  (Spring Modulith 2.1.1 **tidak** punya `ApplicationEvents`). Di cabang `PAID`, **setelah**
  `markPaid`/`saveAndFlush`, panggil `eventPublisher.publishEvent(new PaymentPaidEvent(...))`.
  Publish di dalam transaksi aman: listener `@ApplicationModuleListener` (`AFTER_COMMIT`) baru
  jalan setelah commit, dan publikasi dicatat Modulith untuk recovery — jadi event hanya efektif
  kalau transaksi (termasuk J-1) sukses commit.
  **Jangan** publish di cabang EXPIRED/FAILED untuk sekarang (belum dibutuhkan).
- Idempotency tetap di `processed_events` (sudah ada). Event boleh dikirim >1x → listener donation wajib idempotent.

**Tambahan opsional tapi disarankan (reliability webhook):**
- `WebhookPelRecoveryJob` sudah ada tapi **belum dijadwalkan**. Tambah
  `payment/internal/config/WebhookPelRecoveryScheduler.java` (JobDetail + Trigger, cron
  `payment.webhook.recovery-cron`, default `0 */1 * * * ?`). Tanpa ini webhook gagal tidak pernah di-retry.

### 6.8 Listener donation

```java
@ApplicationModuleListener   // AFTER_COMMIT + async; idempotent
void on(PaymentPaidEvent event) {
    if (!"DONATION".equals(event.type())) return;   // abaikan tipe lain
    donationWriter.markPaid(event);                 // @Transactional, idempotent
}
```
`markPaid`: cari `Donation` by `payment_id`; kalau sudah `PAID` → return (idempotent).
Kalau baru: hitung `durationSeconds` (§3.3), set `PAID`/`paid_at`, buat `OverlayEvent`
(`UNIQUE(donation_id)` mencegah dobel), lalu `dispatcher.dispatch(creatorId)`.
`creatorId` diambil dari `Donation.creator_id` (bukan dari `event.userId`, walau nilainya sama —
donation tidak bergantung pada bentuk payload payment).
Listener **tidak** membaca tabel `payment.*`; hanya dari payload event.

> Tidak ada perubahan di modul `identity` — `userId` sudah tersedia via `CurrentUser.userId()`.

---

## 7. Kontrak HTTP & WebSocket

Base path `/api/v1`. Envelope `ApiResponse<T>` (sukses) / `ErrorResponse` (gagal) sesuai §6
`AGENTS.md`. Auth pakai Firebase (kecuali endpoint publik page & webhook).

### 7.1 REST — donation & page

| Method | Path | Auth | Keterangan |
|---|---|---|---|
| `GET` | `/api/v1/donations/me/page` | auth | `getOrCreateMyPage` (creator, termasuk `overlayKey`) |
| `PUT` | `/api/v1/donations/me/page` | auth | update `display_name/title/description` |
| `POST` | `/api/v1/donations/me/page/overlay-key/rotate` | auth | regenerate `overlay_key` (kembalikan key baru) |
| `GET` | `/api/v1/donation-pages/{creatorId}` | publik | halaman donasi publik (tanpa `overlay_key`) |
| `POST` | `/api/v1/donations` | publik | buat donasi → balikan instruksi bayar |
| `GET` | `/api/v1/donations/{id}` | publik | status donasi (untuk halaman pembayaran) |
| `GET` | `/api/v1/donations/{id}/stream` | publik | **SSE** status pembayaran (lihat §7.4) |
| `GET` | `/api/v1/donations/overlay/queue` | auth | list overlay event (filter `status`) |
| `POST` | `/api/v1/donations/overlay/pause` | auth | pause |
| `POST` | `/api/v1/donations/overlay/resume` | auth | resume |
| `POST` | `/api/v1/donations/overlay/skip` | auth | skip current |
| `POST` | `/api/v1/donations/overlay/{id}/retry` | auth | retry satu event |
| `POST` | `/api/v1/donations/overlay/retry` | auth | retry massal (`?fromStatus=`) |
| `DELETE` | `/api/v1/donations/overlay/queue` | auth | purge antrean PENDING |

`POST /api/v1/donations` request:
```json
{
  "creatorId": "uuid-creator",
  "amount": 50000,
  "donorName": "Anonim",
  "donorEmail": "a@b.com",
  "channelCode": "VA_BCA",
  "type": "YOUTUBE",
  "videoUrl": "https://youtu.be/dQw4w9WgXcQ",
  "message": "semangat!",
  "isAnonymous": false
}
```
Response `data`:
```json
{
  "donationId": "…", "paymentId": "…", "status": "PENDING",
  "amount": 50000, "totalChargedAmount": 50300,
  "channel": "VA_BCA", "instruction": "1234567890",
  "expiresAt": "2026-…"
}
```
`instruction` = `PaymentAttemptResponse.paymentReferenceNumber` (no. VA / QR / redirect URL).
Controller memetakan dari `CreatePaymentResult`.

> **Controller tipis.** `DonationController` hanya memetakan request → parameter service,
> memanggil service internal, membungkus `ApiResponse`. Tidak ada logika bisnis & tidak ada `@Transactional`.

### 7.2 Payload overlay (isi `overlay_events.payload`)

```json
{
  "overlayEventId": "…",
  "donationId": "…",
  "type": "YOUTUBE",
  "donorName": "Budi",
  "isAnonymous": false,
  "amount": 50000,
  "durationSeconds": 100,
  "message": null,
  "videoId": "dQw4w9WgXcQ",
  "canonicalUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
}
```
Untuk `TEXT`: `videoId`/`canonicalUrl` null, `message` diisi, `durationSeconds` dari §3.3.
Payload ditulis saat `OverlayEvent` dibuat (bukan dihitung ulang saat play).

### 7.3 WebSocket

Protokol JSON sederhana (bukan STOMP). Envelope:
```json
{ "v": 1, "type": "…", "id": "…", "data": { } }
```

**Display** — `/ws/overlay/display?key={overlay_key}`
- **Tanpa login.** `overlay_key` **rahasia** (query) hanya untuk **menentukan antrean creator mana**
  yang dikirim ke socket ini. Di `HandshakeInterceptor`: lookup `donation_pages.overlay_key`
  → simpan `creator_id` di attributes sesi; tidak ketemu → tolak handshake (404/401).
  Tidak ada Firebase, tidak ada akun, tidak ada interaksi (OBS pasif). Sesi didaftarkan by `creator_id`.
- Server→client:
  - `{"type":"hello","data":{"creatorId":"…","queueDepth":N,"paused":false}}`
  - `{"type":"play","id":"<overlayEventId>","data":{ …§7.2… }}`
  - `{"type":"paused","data":{"paused":true}}` (opsional, supaya display tahu)
- Client→server:
  - `{"type":"ack","id":"<overlayEventId>"}` — setelah animasi selesai/dihentikan.
  - `{"type":"heartbeat"}` — refresh presence (tiap 10s). Atau pakai WS ping.
- Display **pasif**: tidak ada UI kontrol.

**Control** — `/ws/overlay/control`
- Auth: Firebase ID token (query param `token` atau `Authorization` saat handshake).
  Setelah handshake, `CurrentUser`/principal disimpan di session; verifikasi ownership page.
- Server→client:
  - `{"type":"state","data":{"paused":false,"displayOnline":true,"overlayKey":"…","current":{…},"queue":[…],"history":[…]}}`
  - `{"type":"keyRotated","data":{"overlayKey":"<baru>"}}` — setelah rotate (`overlayKey` **hanya** ke control/owner)
- Client→server:
  - `{"type":"pause"}`, `{"type":"resume"}`, `{"type":"skip"}`
  - `{"type":"retry","id":"…"}` — `PLAYED|SKIPPED|FAILED → PENDING` (masuk antrean normal, bukan head)
  - `{"type":"retryAll","fromStatus":"FAILED"}`
  - `{"type":"purge"}`

Semua command yang mengubah state **tetap** dieksekusi lewat service (`@Transactional`),
bukan di handler. Handler hanya: parse, panggil service, broadcast.

### 7.4 SSE status pembayaran (halaman pembayaran)

- Endpoint `GET /api/v1/donations/{id}/stream` mengembalikan `SseEmitter`.
- Donation module menyimpan emitter lokal per `donationId`; saat `PaymentPaidEvent` masuk
  (listener) → push `event: paid`.
- Multi-instance: publish Redis channel `donation:payment:{donationId}`; setiap node
  meneruskan ke emitter lokalnya. (Pola sama seperti overlay.)
- **Boleh gagal/hilang**: kalau koneksi putus, frontend refresh & panggil `GET /donations/{id}`.
  SSE bukan sumber kebenaran.
- Alternatif bila SSE terlalu merepotkan: `GET /donations/{id}` polling tiap 2s (fallback yang sah).

---

## 8. Konfigurasi

`application.yaml` (semua bisa override env):

```yaml
donation:
  overlay:
    youtube:
      rate-per-second: ${DONATION_YOUTUBE_RATE_PER_SECOND:500}
      min-seconds: ${DONATION_YOUTUBE_MIN_SECONDS:10}
      max-seconds: ${DONATION_YOUTUBE_MAX_SECONDS:1800}
    text:
      max-characters: ${DONATION_TEXT_MAX_CHARACTERS:350}
      chars-per-second: ${DONATION_TEXT_CHARS_PER_SECOND:12}
      min-seconds: ${DONATION_TEXT_MIN_SECONDS:10}
      max-seconds: ${DONATION_TEXT_MAX_SECONDS:60}
    ack-timeout-seconds: ${DONATION_OVERLAY_ACK_TIMEOUT_SECONDS:60}
    auto-requeue-on-timeout: ${DONATION_OVERLAY_AUTO_REQUEUE:true}
    presence-ttl-seconds: ${DONATION_OVERLAY_PRESENCE_TTL_SECONDS:30}
    watchdog-cron: ${DONATION_OVERLAY_WATCHDOG_CRON:0/30 * * * * ?}
```

Redis keys (namespace `donation:`): `{creatorId}` = userId penerima.
`overlay:presence:{creatorId}`, `overlay:paused:{creatorId}`, `overlay:current:{creatorId}`,
pub/sub `overlay:play:{creatorId}`, `overlay:control:{creatorId}`,
`overlay:key-rotated:{creatorId}`, `payment:{donationId}`.

**Dependency baru:** `spring-boot-starter-websocket` (Boot 4). Verifikasi resolve saat Fase 3;
kalau nama artifact berbeda di Boot 4, cari padanan modularnya dan catat di dokumen ini.

---

## 9. Error codes & i18n

Beda dua jalur error:

- **Error non-field** (`ServiceException` + `DonationError implements ErrorCode`) → envelope `ErrorResponse(code, message, null)`.
- **Error validasi field/kombinasi field** → `ValidationException(List<ValidationError>)` (§3.5) →
  envelope `ErrorResponse("validation.failed", headline, errors[field,message])`. **Tidak** bikin
  `DonationError` per aturan validasi.

`donation/internal/exception/DonationError.java` (hanya non-field):

| Konstanta | HTTP | Key | Kapan |
|---|---|---|---|
| `PAGE_NOT_FOUND` | 404 | `donation.page_not_found` | `creatorId`/`overlay_key` tidak ada |
| `DONATION_NOT_FOUND` | 404 | `donation.not_found` | donasi tidak ada |
| `OVERLAY_EVENT_NOT_FOUND` | 404 | `donation.overlay_event_not_found` | retry id tidak ada |
| `OVERLAY_NOT_OWNED` | 403 | `donation.overlay_not_owned` | bukan milik user |
| `DONATION_ALREADY_PAID` | 409 | `donation.already_paid` | (opsional) |
| `INVALID_OVERLAY_COMMAND` | 400 | `donation.invalid_overlay_command` | payload WS tidak valid |

Message key yang dipakai **sebagai `ValidationError.message`** (§3.5): `donation.message_required`,
`donation.video_not_allowed_for_text`, `donation.video_required`, `donation.invalid_youtube_url`,
`donation.message_too_long`, `donation.invalid_type`.

i18n: `src/main/resources/i18n/donation/messages.properties` + `messages_id.properties` (en/id lockstep).
Semua pesan lewat `MessageHelper`; jangan hardcode string.

---

## 10. Fase implementasi (urut, kecil, tiap fase hijau)

> Kerjakan berurutan. Jangan lompat. Tiap fase: `./mvnw test` hijau + update `docs/agents/snapshot.md`
> dan `readme/` bila relevan. Tandai `[x]` saat selesai.

### Fase 0 — Payment domain event + reliability ✅
- [x] `payment/api/event/PaymentPaidEvent.java` + `payment/api/event/package-info.java` (`@NamedInterface("api")`).
- [x] Inject `ApplicationEventPublisher` di `PaymentWebhookService`; publish `PaymentPaidEvent` di cabang PAID setelah commit.
      (Catatan: Spring Modulith 2.1.1 tidak punya `ApplicationEvents`; pakai `ApplicationEventPublisher` — listener `@ApplicationModuleListener` tetap after-commit + tercatat untuk recovery.)
- [x] `payment/internal/config/WebhookPelRecoveryScheduler.java` (jadwalkan `WebhookPelRecoveryJob`) + config `payment.webhook.recovery-cron`.
- [x] Test: event terbit tepat saat PAID, tidak saat EXPIRED/FAILED; idempotent replay tidak menerbitkan dobel.
- [x] Update `docs/agents/snapshot.md` (payment status, event baru).

### Fase 1 — Modul `donation` (page + donation + create payment) — **tanpa `api`** ✅
- [x] `donation/package-info.java`, struktur `internal/**` (config, delivery/http/req, dto, entity, repository, service, exception, util). Tidak ada `api`.
- [x] `DonationError` + i18n (en/id, termasuk message key validasi §9).
- [x] Flyway `V6__donation_tables.sql` (`donation_pages`, `donations`, `overlay_events`).
- [x] Entity `DonationPage`, `Donation`, `OverlayEvent` + enums + repository.
- [x] `YouTubeUrlParser` (util) + test semua bentuk URL & penolakan host lain.
- [x] `OverlayDurationCalculator` (formula §3.3) + test batas (min/cap/tepat).
- [x] `OverlayKeyGenerator` (SecureRandom → base62, unik) + test.
- [x] `DonationPageService`/`DonationPageProvisioning` + `DonationService`/`DonationWriter`
      (page get/create via `CurrentUser.userId()`, createDonation → `PaymentApi.createPayment`,
      getDonation, rotate `overlay_key`). Validasi §3.5 `private` + `ValidationException`.
- [x] Endpoint rotate `overlay_key` (owner) + broadcast `donation:overlay:key-rotated:{creatorId}` (menutup sesi display lama, control dapat `keyRotated`) — dikerjakan lintas Fase 2/3.
- [x] `DonationPageController` + `DonationController` (thin) + req DTO + security permitAll (publik) di `SecurityConfig`.
- [x] Test: `DonationServiceTest` (validasi field+message, buat PENDING + command payment benar),
      util tests. `ModularityTests` hijau (payment `api/dtos` & `api/enums` di-`@NamedInterface`).

### Fase 2 — Overlay queue + dispatcher + watchdog + listener ✅
- [x] `DonationPaidListener` (`@ApplicationModuleListener`, idempotent) → `DonationPaidService.handlePaid` (markPaid + `OverlayEvent`) → dispatch after-commit.
- [x] `OverlayWriter` (`@Transactional`, claim `FOR UPDATE SKIP LOCKED` by `creator_id`, transisi §5) + `OverlayDispatcher`.
- [x] Redis presence/paused/current helper (`OverlayStateStore`).
- [x] `OverlayBroadcaster` + `RedisOverlayBroadcaster` (pub/sub `donation:overlay:play:{creatorId}`).
- [x] `OverlayAckWatchdogJob` + `OverlayScheduler` (Quartz, cron `donation.overlay.watchdog-cron`).
- [x] `OverlayPayloadFactory` (payload §7.2) + `applyPayload` di entity.
- [x] Control methods di `OverlayQueueService` (list/pause/resume/skip/retry/retryAll/purge) untuk controller & WS handler.
- [x] Test: hanya PAID masuk antrean; idempotent; dispatch gating (paused/playing/offline); retry ownership; skip.
      (`OverlayDispatcherTest`, `DonationPaidServiceTest`, `OverlayQueueServiceTest`.)
- [x] Endpoint HTTP kontrol overlay (queue/pause/resume/skip/retry/purge) → dikerjakan di Fase 4.

### Fase 3 — WebSocket display & control ✅
- [x] Dependency `spring-boot-starter-websocket` (Boot 4, resolve OK).
- [x] `OverlayWebSocketConfig` (`@EnableWebSocket`, `/ws/overlay/display` + `/ws/overlay/control`) + `OverlayHandshakeInterceptor`.
- [x] `OverlayDisplayHandler` (tanpa login; `overlay_key` → resolve `creator_id`) + `OverlayControlHandler` (token Firebase di-resolve ke user saat handshake, lalu `creatorId` dari sesi).
- [x] `OverlaySessionRegistry` (lokal) + `OverlayRedisSubscriber` + `OverlayRedisConfig` (`redisMessageListenerContainer`, pattern `donation:overlay:play:*`).
- [x] Protokol §7.3 (hello/play/ack/heartbeat; state/pause/resume/skip/retry/retryAll/purge).
- [x] `OverlayPlaybackService` (connected/heartbeat/disconnected/ack) + `OverlayWriter.markPlayedIfCurrent`.
- [x] Security config `permit /ws/**`; resolusi key/token di interceptor (bukan filter chain).
- [x] `identity/api/dtos` di-`@NamedInterface("api")` (dibutuhkan untuk `UserPrincipal`/`UserStatus`).
- [x] Test: handshake (display key + control token), registry, playback, subscriber. `ModularityTests` hijau.

### Fase 4 — Realtime pembayaran + kontrol REST + polish ✅
- [x] SSE `GET /api/v1/donations/{id}/stream` + `DonationStatusStreamService` + Redis fan-out `donation:payment:{id}` (`DonationStatusRedisSubscriber`).
- [x] `OverlayControlController` (`/api/v1/donations/overlay/*`) — list/pause/resume/skip/retry/retryAll/purge (pelengkap WS).
- [x] i18n lengkap (en/id), `readme/donation.md`.
- [x] `PayoutProvider` mock `"MOCK"` (`MockPayoutProvider`) untuk withdraw di dev, diaktifkan `payment.payout.mock-enabled=true` (`PAYMENT_PAYOUT_MOCK_ENABLED`) — alur ledger tetap (payout langsung `COMPLETED` → J-6).
- [x] Definition of Done (§11) — lihat status di bawah.

---

## 11. Definition of Done

- [x] `./mvnw test` hijau (131 test), termasuk `ModularityTests` (batas modul utuh) & `ApplicationTests`.
- [x] `donation` hanya akses `payment.api` + `identity.api`; tidak menyentuh `internal`/schema modul lain.
- [x] Tidak ada `@Scheduled`; semua job Quartz clustered (`OverlayAckWatchdogJob`).
- [x] Semua endpoint sukses = `ApiResponse`, gagal = `ErrorResponse` dari handler global tunggal.
- [x] Semua error domain = `DonationError` + i18n en/id lockstep (validasi field via `ValidationException`).
- [x] `@Transactional` hanya di service/writer (bukan controller/repository/WS handler).
- [x] Uang = `bigint`. ID user-facing = UUID v7. Enum = varchar.
- [x] Event listener idempotent; `overlay_events.donation_id` UNIQUE; dispatch claim atomik (`FOR UPDATE SKIP LOCKED`).
- [x] Overlay hanya berisi `PAID`; FIFO; ack/timeout/retry/pause/skip teruji (unit).
- [x] Formula durasi §3.3 & validasi YouTube §3.4 teruji batasnya.
- [x] `docs/agents/snapshot.md` + `readme/donation.md` diperbarui.

---

## 12. Keputusan yang sudah dikunci

1. Overlay hanya menampilkan donasi **PAID**.
2. Backlog saat display connect → **drain semua PENDING, FIFO**.
3. Tujuan/penerima donasi = **`creator_id`** = `userId` (dari `CurrentUser.userId()`). 1 creator = 1 page.
   Key display OBS = **`overlay_key` rahasia** (acak), **bukan** userId/authId.
4. Retry event `PLAYED` → reset ke `PENDING`, **masuk antrean normal** (bukan head).
5. Tipe = **`TEXT`** (maks 350 char) & **`YOUTUBE`** (bukan `VIDEO`, demi fitur upload nanti).
6. Durasi: youtube `max(10, min(amount/500, 1800))` detik; text `clamp(ceil(chars/12), 10, 60)` detik.
7. OBS display **pasif**; kontrol di halaman terpisah via WebSocket.
8. Donation page auto-provision saat creator pertama akses; tanpa KYC.
9. Modul `donation` **tanpa `api` publik** (tidak ada modul lain yang depend ke `donation`).
10. Validasi domain/kombinasi field di **service** (`private`), hasil = `ValidationException`
    (`errors[].field` + `errors[].message`) supaya frontend sekali parse.
11. **Tidak ada perubahan `identity`** — cukup `CurrentUser.userId()` yang sudah ada.
12. **`overlay_key` bisa dirotasi** creator (`POST .../overlay-key/rotate`); key lama langsung mati,
    sesi display lama ditutup; antrean tidak terpengaruh.

---

## 13. Titik yang perlu diverifikasi saat implementasi (risiko)

1. **Artifact WebSocket Boot 4** — pastikan nama dependency benar dan `WebSocketConfigurer` tersedia di `starter-webmvc`+`starter-websocket`.
2. **Handshake** — Spring Security memfilter request upgrade; pastikan `/ws/**` di-`permitAll` dan resolusi key / verifikasi token dilakukan di `HandshakeInterceptor`, hasil disimpan di `session.getAttributes()`. Display **tanpa login** (cukup resolve `overlay_key`).
3. **Presence vs connect race** — display connect lalu langsung `dispatch`; pastikan presence sudah ter-set sebelum dispatch dipanggil.
4. **Claim atomik** — `FOR UPDATE SKIP LOCKED` harus native query Postgres; jangan pakai derived query (tidak mendukung locking + skip).
5. **Redis pub/sub fan-out** — kalau hanya 1 instance (dev), boleh short-circuit ke registry lokal; tetap sediakan jalur pub/sub agar multi-instance benar.
6. **`ApplicationEvents` dalam transaksi** — pastikan event benar-benar after-commit; uji dengan transaksi yang di-rollback (event tidak boleh terkirim).
7. **Idempotency listener** — `UNIQUE(donation_id)` + cek `status` sebelum markPaid; event boleh datang >1x.
8. **`overlay_key` rahasia** — jangan di-log, jangan dikembalikan endpoint publik; hanya `GET /page` (milik sendiri).
   Rotasi key (opsional) bisa ditambah nanti.
9. **Donation tanpa `api`** — pastikan `ModularityTests.verify()` tetap lolos; modul CLOSED tanpa named interface sah.
   Jika butuh key display rahasia, tambah kolom `display_key` acak terpisah (tanpa ubah identifier creator).
