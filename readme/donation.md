# Donation — Konsep & Alur

> Modul `donation` adalah **consumer** dari `payment`. Creator punya halaman
> donasi + overlay OBS. Donasi (TEXT / YOUTUBE) diproses lewat `PaymentApi`
> (`type=DONATION`); saat payment `PAID`, donasi masuk **antrean overlay** milik
> creator dan diputar di OBS melalui WebSocket.
>
> Modul ini **tanpa `api` publik** (tidak ada modul lain yang bergantung padanya).
> Konvensi: [`../../AGENTS.md`](../../AGENTS.md). Rencana kerja: [`../../todo.md`](../../todo.md).

---

## 1. Konsep

| Istilah | Arti |
|---|---|
| **Donation page** | Halaman milik creator (1 creator = 1 page). Dibuat otomatis saat creator pertama akses. |
| **overlay_key** | String rahasia per creator untuk display OBS (`/stream?key=…`). Bisa dirotasi (regenerate). |
| **Donation** | Donasi dari donor. Tipe `TEXT` (pesan, maks 350 char) atau `YOUTUBE` (URL video). |
| **Overlay event** | Satu item antrean overlay; dibuat saat donasi `PAID`. |
| **Display** | Klien OBS (pasif) — terima `play`, putar, balas `ack`. |
| **Control** | Halaman kontrol owner (login) — pause/skip/retry lewat WebSocket/REST. |

Penerima donasi diidentifikasi dengan **`userId`** creator (`CurrentUser.userId()`),
bukan authId. Display OBS diakses dengan **`overlay_key` rahasia** (tanpa login).

---

## 2. Alur uang & overlay

```
Donor → POST /api/v1/donations  (creatorId, amount, type, message|videoUrl, channelCode)
   → donation simpan PENDING + PaymentApi.createPayment(type=DONATION, userId=creator, payerId=null)
   → balas instruksi bayar (no VA/QR) + donor buka SSE GET /donations/{id}/stream
Donor bayar → webhook PG → payment worker → PaymentPaidEvent (after commit)
   → DonationPaidListener: donasi PAID + buat OverlayEvent(PENDING) + dispatch
   → Android (SSE) dapat "paid"
OverlayDispatcher (FIFO, satu per satu):
   paused? / ada PLAYING? / display offline? → stop
   claim PENDING tertua (FOR UPDATE SKIP LOCKED) → PLAYING → publish Redis
   → node pemegang socket display mengirim {"type":"play",...}
Display putar sesuai durationSeconds → kirim {"type":"ack","id":...} → PLAYED → lanjut
```

---

## 3. Aturan bisnis

- **Hanya donasi `PAID`** yang masuk antrean overlay.
- Tipe: `TEXT` (maks **350** karakter) & `YOUTUBE` (URL YouTube valid, id 11 char).
- **Durasi** (`OverlayDurationCalculator`):
  - YOUTUBE: `max(10, min(amount / 500, 1800))` detik. Video lebih pendek → putar penuh.
  - TEXT: `clamp(ceil(chars / 12), 10, 60)` detik.
- Validasi kombinasi field (TEXT tanpa pesan, YOUTUBE tanpa URL, URL bukan YouTube, dsb.)
  dilakukan **di service** dan mengembalikan `ValidationException` (`errors[].field` +
  `errors[].message`) — frontend cukup memakai envelope validasi standar.

---

## 4. Endpoint

**Halaman & donasi**

| Method | Path | Auth | Keterangan |
|---|---|---|---|
| `GET` | `/api/v1/donations/me/page` | owner | halaman sendiri (termasuk `overlayKey`) |
| `PUT` | `/api/v1/donations/me/page` | owner | update `displayName/title/description/imageUrl/slug` |
| `POST` | `/api/v1/donations/me/page/overlay-key/rotate` | owner | regenerate `overlay_key` |
| `GET` | `/api/v1/donations/me?cursor=&size=` | owner | riwayat donasi creator (dashboard), terbaru dulu |
| `GET` | `/api/v1/donation-pages/{slug}` | publik | halaman donasi publik (tanpa `overlayKey`), by username |
| `POST` | `/api/v1/donations` | publik | buat donasi → instruksi bayar |
| `GET` | `/api/v1/donations/{id}` | publik | status donasi |
| `GET` | `/api/v1/donations/{id}/stream` | publik | **SSE** status (event `paid`) |

> `slug` = username publik halaman (unik, lowercase, 3–60 char `[a-z0-9-]`). Di-generate
> otomatis saat page pertama dibuat; bisa diganti lewat `PUT .../page` (`409` bila sudah dipakai).
> `imageUrl` = avatar/URL gambar creator untuk halaman publik. List memakai
> `CursorPage<T>` (`items/hasNext/nextCursor`) — keyset by id; lanjut dengan `?cursor=<nextCursor>`.

**Kontrol overlay (owner)** — realtime juga lewat `/ws/overlay/control`

| Method | Path | Keterangan |
|---|---|---|
| `GET` | `/api/v1/donations/overlay/queue?status=` | list antrean |
| `POST` | `/api/v1/donations/overlay/pause` · `/resume` · `/skip` | kontrol playback |
| `POST` | `/api/v1/donations/overlay/{id}/retry` · `/retry?fromStatus=` | retry (reset ke PENDING) |
| `DELETE` | `/api/v1/donations/overlay/queue` | purge PENDING |

---

## 5. WebSocket

Envelope: `{ "v": 1, "type": "…", "id": "…", "data": { } }`.

- **Display** `/ws/overlay/display?key={overlay_key}` — **tanpa login**; key di-resolve ke
  `creatorId` saat handshake. Server→client: `hello`, `play`. Client→server: `ack`, `heartbeat`.
- **Control** `/ws/overlay/control?token={firebaseIdToken}` — login. Server→client: `state`.
  Client→server: `pause`, `resume`, `skip`, `retry`, `retryAll`, `purge`.

Payload `play` (`data`): `overlayEventId, donationId, type, donorName, isAnonymous, amount,
durationSeconds, message, videoId, canonicalUrl`.

---

## 6. Multi-instance & reliability

- **Antrean durable di DB** (`overlay_events`) — display offline → item tetap `PENDING`.
- **Redis** untuk state transient (presence/paused/current) + fan-out (`donation:overlay:play:*`,
  `donation:payment:*`).
- **Klaim atomik** `FOR UPDATE SKIP LOCKED` per `creator_id` → aman lintas instance.
- **Watchdog** Quartz (`OverlayAckWatchdogJob`) mengembalikan event `PLAYING` yang lewat timeout ack.
- **At-least-once**: `PaymentPaidEvent` listener idempotent (`UNIQUE(donation_id)` + cek status).

---

## 7. Konfigurasi (`donation.overlay.*`)

Semua punya default; bisa dioverride env (`DONATION_*`):

```yaml
donation:
  overlay:
    youtube: { rate-per-second: 500, min-seconds: 10, max-seconds: 1800 }
    text:    { max-characters: 350, chars-per-second: 12, min-seconds: 10, max-seconds: 60 }
    ack-timeout-seconds: 60
    auto-requeue-on-timeout: true
    presence-ttl-seconds: 30
    watchdog-cron: "0/30 * * * * ?"
```
