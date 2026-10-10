CREATE SCHEMA IF NOT EXISTS donation;

-- =====================================================================
-- DONATION — halaman creator, donasi, dan antrean overlay
-- =====================================================================

-- Satu creator = satu halaman donasi (1:1). creator_id = userId (identity.users.id),
-- disimpan sebagai UUID tanpa FK lintas modul (AGENTS.md §3 persistence).
-- overlay_key = RAHASIA untuk display OBS; bisa dirotasi creator (regenerate).
-- slug = username publik (unik, lowercase) untuk URL halaman; image_url = avatar creator.
CREATE TABLE donation.donation_pages
(
    id           UUID         PRIMARY KEY,
    creator_id   UUID         NOT NULL,
    overlay_key  VARCHAR(64)  NOT NULL,
    slug         VARCHAR(60)  NOT NULL,
    display_name VARCHAR(120),
    title        VARCHAR(200),
    description  TEXT,
    image_url    TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_donation_pages_creator     UNIQUE (creator_id),
    CONSTRAINT ux_donation_pages_overlay_key UNIQUE (overlay_key),
    CONSTRAINT ux_donation_pages_slug        UNIQUE (slug)
);

-- Satu baris = satu donasi. Tujuan/penerima = creator_id. payment_id = referensi
-- lintas modul ke payment.payments.id (UUID, tanpa FK cross-schema).
-- idempotency_key = kunci idempotensi pembuatan donasi (unique).
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
    CONSTRAINT ux_donations_idem     UNIQUE (idempotency_key),
    CONSTRAINT ck_donations_type     CHECK (type IN ('TEXT', 'YOUTUBE')),
    CONSTRAINT ck_donations_status   CHECK (status IN ('PENDING', 'PAID', 'EXPIRED', 'FAILED')),
    CONSTRAINT ck_donations_payload  CHECK (
        (type = 'TEXT'    AND message IS NOT NULL AND video_id IS NULL)
     OR (type = 'YOUTUBE' AND video_id IS NOT NULL)
    )
);

-- Antrean query: ambil donasi creator tertentu; cek status.
CREATE INDEX ix_donations_creator ON donation.donations (creator_id, status, created_at, id);
-- 1 payment = maks 1 donasi (partial: payment_id boleh NULL sebelum payment dibuat).
CREATE UNIQUE INDEX ux_donations_payment ON donation.donations (payment_id) WHERE payment_id IS NOT NULL;

-- Antrean overlay durable, per creator. Satu donasi = maksimal satu overlay event.
-- payload = JSON siap kirim ke display. FIFO: ORDER BY created_at, id.
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
