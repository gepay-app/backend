CREATE SCHEMA IF NOT EXISTS payment;


-- =====================================================================
-- PAYMENT — master & routing
-- =====================================================================

-- Master payment gateway / disbursement provider. Satu baris = satu vendor
-- (Midtrans, Flip, ...). Logika bisnis TIDAK boleh mengimpor tipe vendor;
-- adapter per provider memetakan bahasa vendor -> kontrak internal.
CREATE TABLE payment.providers
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,               -- MIDTRANS | FLIP | ...
    name            VARCHAR(100) NOT NULL,
    supports_payin  BOOLEAN      NOT NULL DEFAULT FALSE,
    supports_payout BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_providers_code UNIQUE (code)
);

INSERT INTO payment.providers (code, name, supports_payin, supports_payout)
VALUES ('MIDTRANS', 'Midtrans', TRUE, FALSE),
       ('FLIP', 'Flip', FALSE, TRUE)
ON CONFLICT ON CONSTRAINT ux_providers_code DO NOTHING;

-- Channel logis yang stabil & tidak terikat provider. FE/API merujuk ke sini,
-- bukan ke PG. Satu channel boleh dilayani >1 PG (multi-route).
CREATE TABLE payment.channels
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    type         VARCHAR(20)  NOT NULL, -- VA | QRIS | EWALLET | BANK_TRANSFER | PAYOUT_BANK | PAYOUT_EWALLET
    direction    VARCHAR(10)  NOT NULL, -- PAYIN (masuk) | PAYOUT (keluar)
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_channels_code UNIQUE (code)
);

INSERT INTO payment.channels (code, display_name, type, direction)
VALUES ('VA_PERMATA', 'Transfer PERMATA Virtual Account', 'VA', 'PAYIN'),
       ('VA_BCA', 'Transfer BCA Virtual Account', 'VA', 'PAYIN'),
       ('VA_BNI', 'Transfer BNI Virtual Account', 'VA', 'PAYIN'),
       ('VA_BRI', 'Transfer BRI Virtual Account', 'VA', 'PAYIN'),
       ('VA_CIMB', 'Transfer CIMB Virtual Account', 'VA', 'PAYIN'),
       ('QRIS', 'QRIS', 'QRIS', 'PAYIN'),
       -- payout
       ('BANK_BCA', 'Transfer Bank BCA', 'PAYOUT_BANK', 'PAYOUT'),
       ('BANK_BNI', 'Transfer Bank BNI', 'PAYOUT_BANK', 'PAYOUT'),
       ('BANK_BRI', 'Transfer Bank BRI', 'PAYOUT_BANK', 'PAYOUT'),
       ('BANK_CIMB', 'Transfer Bank CIMB', 'PAYOUT_BANK', 'PAYOUT'),
       ('BANK_MANDIRI', 'Transfer Bank MANDIRI', 'PAYOUT_BANK', 'PAYOUT'),
       ('EWALLET_GOPAY', 'Transfer E-Wallet GOPAY', 'PAYOUT_EWALLET', 'PAYOUT'),
       ('EWALLET_SHOPEE', 'Transfer E-Wallet SHOPEE', 'PAYOUT_EWALLET', 'PAYOUT'),
       ('EWALLET_OVO', 'Transfer E-Wallet OVO', 'PAYOUT_EWALLET', 'PAYOUT')
ON CONFLICT ON CONSTRAINT ux_channels_code DO NOTHING;

-- Routing: channel logis mana dilayani provider mana, pakai kode provider apa.
-- Ini sekaligus MENEMPELKAN kebijakan settlement (T+n) per route, karena timing
-- dana cair beda-beda antar provider & channel — bukan angka global.
CREATE TABLE payment.channel_routes
(
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id             BIGINT      NOT NULL REFERENCES payment.providers (id),
    channel_id              BIGINT      NOT NULL REFERENCES payment.channels (id),
    provider_channel_code   VARCHAR(60) NOT NULL,             -- kode spesifik milik PG utk channel ini
    min_amount              BIGINT      NOT NULL DEFAULT 0,
    max_amount              BIGINT      NOT NULL DEFAULT 0,   -- 0 = tanpa batas atas
    priority                INT         NOT NULL DEFAULT 100, -- kecil = diprioritaskan saat failover

    -- Kebijakan settlement (kapan dana diperkirakan cair ke platform).
    settlement_delay_days   INT         NOT NULL DEFAULT 3,   -- T+n, SELALU dalam hari kerja
    settlement_target       VARCHAR(20) NOT NULL DEFAULT 'BANK', -- BANK | PROVIDER_BALANCE

    is_active               BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_routes_provider_channel UNIQUE (provider_id, channel_id)
);

INSERT INTO payment.channel_routes
(provider_id, channel_id, provider_channel_code, min_amount, max_amount, priority,
 settlement_delay_days, settlement_target)
SELECT p.id AS provider_id,
       c.id AS channel_id,
       v.provider_channel_code,
       v.min_amount,
       v.max_amount,
       v.priority,
       v.settlement_delay_days,
       v.settlement_target
FROM (VALUES
          -- PAYIN -> MIDTRANS (settlement: T+3 hari kerja, ke bank)
          ('MIDTRANS', 'VA_PERMATA', 'permata', 10000, 50000000, 100, 3, 'BANK'),
          ('MIDTRANS', 'VA_BCA', 'bca', 10000, 50000000, 100, 3, 'BANK'),
          ('MIDTRANS', 'VA_BNI', 'bni', 10000, 50000000, 100, 3, 'BANK'),
          ('MIDTRANS', 'VA_BRI', 'bri', 10000, 50000000, 100, 3, 'BANK'),
          ('MIDTRANS', 'VA_CIMB', 'cimb', 10000, 50000000, 100, 3, 'BANK'),
          ('MIDTRANS', 'QRIS', 'qris', 1000, 50000000, 100, 3, 'BANK'),

          -- PAYOUT -> FLIP (bukan settlement; default saja)
          ('FLIP', 'BANK_BCA', 'bca', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'BANK_BNI', 'bni', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'BANK_BRI', 'bri', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'BANK_CIMB', 'cimb', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'BANK_MANDIRI', 'mandiri', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'EWALLET_GOPAY', 'gopay', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'EWALLET_SHOPEE', 'shopeepay', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE'),
          ('FLIP', 'EWALLET_OVO', 'ovo', 10000, 50000000, 100, 0, 'PROVIDER_BALANCE')) AS v(provider_code, channel_code, provider_channel_code,
                                                                              min_amount, max_amount, priority,
                                                                              settlement_delay_days, settlement_target)
         JOIN payment.providers p ON p.code = v.provider_code
         JOIN payment.channels c ON c.code = v.channel_code
ON CONFLICT ON CONSTRAINT ux_routes_provider_channel DO NOTHING;

-- =====================================================================
-- PAYMENT — fee (versi)
-- =====================================================================

CREATE TABLE payment.fee_configs
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_type       VARCHAR(30) NOT NULL,                     -- PLATFORM_DONATION | PLATFORM_CONTENT
    -- | PLATFORM_WITHDRAWAL | GATEWAY_PROCESSING | PAYOUT
    provider_id    BIGINT REFERENCES payment.providers (id), -- diisi utk GATEWAY_PROCESSING/PAYOUT
    channel_id     BIGINT REFERENCES payment.channels (id),  -- diisi utk GATEWAY_PROCESSING/PAYOUT
    fixed_amount   BIGINT      NOT NULL DEFAULT 0,
    percentage_bps INT         NOT NULL DEFAULT 0,
    vat_bps        INT         NOT NULL DEFAULT 0,           -- PPN atas (fixed+percentage), 1100 = 11%
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,
    note           VARCHAR(240),
    created_by     VARCHAR(64),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_fee_scope CHECK (
        (fee_type IN ('PLATFORM_DONATION', 'PLATFORM_CONTENT', 'PLATFORM_WITHDRAWAL')
            AND provider_id IS NULL AND channel_id IS NULL)
            OR fee_type IN ('GATEWAY_PROCESSING', 'PAYOUT')
        ),
    CONSTRAINT ck_fee_rates CHECK (
        fixed_amount >= 0 AND percentage_bps >= 0 AND vat_bps >= 0
            AND percentage_bps <= 10000 AND vat_bps <= 10000
        )
);
CREATE INDEX ix_fee_configs_lookup
    ON payment.fee_configs (fee_type, provider_id, channel_id, effective_from DESC);

INSERT INTO payment.fee_configs
(fee_type, provider_id, channel_id, fixed_amount, percentage_bps, vat_bps, effective_from, note)
SELECT v.fee_type,
       p.id,
       c.id,
       v.fixed_amount,
       v.percentage_bps,
       v.vat_bps,
       v.effective_from,
       v.note
FROM (VALUES
          -- PLATFORM FEES (Global - Tanpa Provider/Channel)
          ('PLATFORM_DONATION', NULL, NULL, 1000, 500, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Platform fee donasi ((1000+5%) + PPN 11%)'),
          ('PLATFORM_CONTENT', NULL, NULL, 1000, 500, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Platform fee konten ((1000+5%) + PPN 11%)'),
          ('PLATFORM_WITHDRAWAL', NULL, NULL, 3000, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Platform withdrawal fee flat Rp3.000'),

          -- GATEWAY_PROCESSING (Midtrans - PAYIN Channels)
          ('GATEWAY_PROCESSING', 'MIDTRANS', 'VA_PERMATA', 4000, 0, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Midtrans Permata VA (Rp4.000 + PPN)'),
          ('GATEWAY_PROCESSING', 'MIDTRANS', 'VA_BCA', 4000, 0, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Midtrans BCA VA (Rp4.000 + PPN)'),
          ('GATEWAY_PROCESSING', 'MIDTRANS', 'VA_BNI', 4000, 0, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Midtrans BNI VA (Rp4.000 + PPN)'),
          ('GATEWAY_PROCESSING', 'MIDTRANS', 'VA_BRI', 4000, 0, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Midtrans BRI VA (Rp4.000 + PPN)'),
          ('GATEWAY_PROCESSING', 'MIDTRANS', 'VA_CIMB', 4000, 0, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Midtrans CIMB VA (Rp4.000 + PPN)'),
          ('GATEWAY_PROCESSING', 'MIDTRANS', 'QRIS', 0, 70, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Midtrans QRIS (0.7% + PPN)'),

          -- PAYOUT (Flip - PAYOUT Channels)
          ('PAYOUT', 'FLIP', 'BANK_BCA', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank BCA payout (Rp2.500)'),
          ('PAYOUT', 'FLIP', 'BANK_BNI', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank BNI payout (Rp2.500)'),
          ('PAYOUT', 'FLIP', 'BANK_BRI', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank BRI payout (Rp2.500)'),
          ('PAYOUT', 'FLIP', 'BANK_CIMB', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank CIMB payout (Rp2.500)'),
          ('PAYOUT', 'FLIP', 'EWALLET_GOPAY', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip GoPay payout (Rp2.500)'),
          ('PAYOUT', 'FLIP', 'EWALLET_SHOPEE', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip ShopeePay payout (Rp2.500)'),
          ('PAYOUT', 'FLIP', 'EWALLET_OVO', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip OVO payout (Rp2.500)')) AS v(fee_type, provider_code, channel_code, fixed_amount, percentage_bps,
                                              vat_bps, effective_from, note)
         LEFT JOIN payment.providers p ON p.code = v.provider_code
         LEFT JOIN payment.channels c ON c.code = v.channel_code
WHERE NOT EXISTS (SELECT 1
                  FROM payment.fee_configs f
                  WHERE f.fee_type = v.fee_type
                    AND f.effective_from = v.effective_from
                    AND f.provider_id IS NOT DISTINCT FROM p.id
                    AND f.channel_id IS NOT DISTINCT FROM c.id);

-- Override fee per user (VIP). Lebih diprioritaskan dari fee_configs saat resolve.
CREATE TABLE payment.user_fee_overrides
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        UUID         NOT NULL,
    fee_type       VARCHAR(30)  NOT NULL,
    fixed_amount   BIGINT       NOT NULL DEFAULT 0,
    percentage_bps INT          NOT NULL DEFAULT 0,
    vat_bps        INT          NOT NULL DEFAULT 0,
    effective_from TIMESTAMPTZ  NOT NULL,
    effective_to   TIMESTAMPTZ,
    reason         VARCHAR(300) NOT NULL,
    approved_by    UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_override_rates CHECK (
        fixed_amount >= 0 AND percentage_bps >= 0 AND vat_bps >= 0
            AND percentage_bps <= 10000 AND vat_bps <= 10000
        )
);
CREATE INDEX ix_user_fee_overrides_lookup
    ON payment.user_fee_overrides (user_id, fee_type, effective_from DESC);

-- =====================================================================
-- PAYMENT — hari libur (untuk hitung hari kerja settlement)
-- =====================================================================

-- Sumber kebenaran "hari kerja" untuk T+n settlement. Sabtu/Minggu otomatis
-- bukan hari kerja; hari libur nasional di-seed di sini (per SKB). Kolom ini
-- DATA, bukan logika — update tiap tahun tanpa ubah kode.
CREATE TABLE payment.holidays
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    date         DATE        NOT NULL,
    name         VARCHAR(120) NOT NULL,
    country_code VARCHAR(2)  NOT NULL DEFAULT 'ID',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_holidays_date UNIQUE (date, country_code)
);

-- Seed awal: hari libur NASIONAL Indonesia yang pasti (fixed). Idul Fitri, Idul
-- Adha, dsb (tanggal mengambang) WAJIB ditambahkan per SKB tiap tahun.
INSERT INTO payment.holidays (date, name, country_code)
VALUES ('2026-01-01', 'Tahun Baru Masehi', 'ID'),
       ('2026-05-01', 'Hari Buruh Internasional', 'ID'),
       ('2026-08-17', 'Hari Kemerdekaan RI', 'ID'),
       ('2026-12-25', 'Hari Raya Natal', 'ID')
ON CONFLICT ON CONSTRAINT ux_holidays_date DO NOTHING;

-- =====================================================================
-- PAYMENT — settlement (BATCH header; dibuat dari BUKTI, bukan webhook PG)
-- =====================================================================

-- Header satu batch pencairan dana dari PG ke platform (mis. satu pencairan
-- Midtrans / satu mutasi bank). Transaksi yang tercakup dipilih otomatis by rule
-- (fallback: match order_id dari CSV/report), bukti nominal datang dari mutasi bank
-- / konfirmasi MAP. Tidak ada webhook PG yang bisa membuktikan "dana sudah cair"
-- untuk Midtrans, jadi batch selalu dibuat manusia/bukti.
CREATE TABLE payment.settlements
(
    id                      UUID PRIMARY KEY,
    provider_id             BIGINT      NOT NULL REFERENCES payment.providers (id),
    status                  VARCHAR(20) NOT NULL,                  -- PENDING | CONFIRMED | CANCELLED
    external_settlement_id  VARCHAR(120),                          -- id batch dari PG (kalau ada)
    expected_amount         BIGINT,                                -- Σ net transaksi yang di-match
    actual_amount           BIGINT,                                -- nominal yang BENAR-BENAR masuk (bukti)
    variance_amount         BIGINT,                                -- = actual_amount - expected_amount
    settlement_target       VARCHAR(20),                           -- BANK | PROVIDER_BALANCE
    period_start            DATE,                                  -- rentang data laporan (opsional)
    period_end              DATE,
    actual_settled_at       TIMESTAMPTZ,                           -- waktu dana masuk (bukti)
    evidence_source         VARCHAR(30),                           -- API | REPORT_FILE | BANK_STATEMENT | MANUAL
    evidence_reference      VARCHAR(200),                          -- ref mutasi / file / tiket
    raw_evidence            JSONB,
    confirmed_at            TIMESTAMPTZ,
    created_by              UUID,                                  -- admin yang membuat
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_settlements_external
    ON payment.settlements (provider_id, external_settlement_id)
    WHERE external_settlement_id IS NOT NULL;

-- =====================================================================
-- PAYMENT — transaksi & attempt
-- =====================================================================

-- Niat transaksi bisnis (donasi/content) + SNAPSHOT fee. Fee TIDAK dihitung
-- ulang saat webhook; semua komponen disimpan di sini untuk audit.
CREATE TABLE payment.payments
(
    id                             UUID PRIMARY KEY,                                             -- UUID v7, id yang tampil ke user
    idempotency_key                VARCHAR(160) NOT NULL,
    type                           VARCHAR(20)  NOT NULL,                                        -- DONATION | CONTENT_PURCHASE
    status                         VARCHAR(20)  NOT NULL,                                        -- INITIATED | PENDING | PAID | EXPIRED
    -- | FAILED | CANCELLED
    -- | PARTIALLY_REFUNDED | REFUNDED
    user_id                        UUID         NOT NULL,                                        -- user CREATOR PENERIMA hak (payee)
    payer_id                       UUID,                                                         -- user PEMBAYAR; NULL = anonim
    provider_id                    BIGINT       NOT NULL REFERENCES payment.providers (id),       -- snapshot PG (dari route)
    channel_id                     BIGINT       NOT NULL REFERENCES payment.channels (id),
    channel_route_id               BIGINT       NOT NULL REFERENCES payment.channel_routes (id),
    gross_amount                   BIGINT       NOT NULL CHECK (gross_amount > 0),

    -- snapshot fee PG (pass-through; tidak masuk ledger, hanya untuk rekonsiliasi)
    gateway_fee_config_id          BIGINT REFERENCES payment.fee_configs (id),
    pg_fixed_fee_amount            BIGINT       NOT NULL DEFAULT 0,
    pg_percentage_fee_bps          INT          NOT NULL DEFAULT 0,
    pg_percentage_fee_amount       BIGINT       NOT NULL DEFAULT 0,
    pg_vat_bps                     INT          NOT NULL DEFAULT 0,
    pg_vat_amount                  BIGINT       NOT NULL DEFAULT 0,
    pg_fee_amount                  BIGINT       NOT NULL DEFAULT 0,

    -- snapshot fee platform
    platform_fee_config_id         BIGINT REFERENCES payment.fee_configs (id),
    user_fee_override_id           BIGINT REFERENCES payment.user_fee_overrides (id),
    platform_fixed_fee_amount      BIGINT       NOT NULL DEFAULT 0,
    platform_percentage_fee_bps    INT          NOT NULL DEFAULT 0,
    platform_percentage_fee_amount BIGINT       NOT NULL DEFAULT 0,
    platform_vat_bps               INT          NOT NULL DEFAULT 0,
    platform_vat_amount            BIGINT       NOT NULL DEFAULT 0,
    platform_fee_amount            BIGINT       NOT NULL DEFAULT 0,

    total_charged_amount           BIGINT       NOT NULL,                                        -- dibayar pembayar (gross + pg fee)
    net_creator_amount             BIGINT       NOT NULL,                                        -- hak creator (gross - platform fee)
    expected_settlement_amount     BIGINT       NOT NULL,                                        -- uang yang diharap settle dari PG

    -- settlement tracking (batch)
    expected_settlement_date       DATE,                                                         -- paid_at + T+n hari kerja (dari route)
    settlement_id                  UUID REFERENCES payment.settlements (id),                    -- batch settlement yang memuat payment ini
    settled_at                     TIMESTAMPTZ,                                                 -- diisi saat batch CONFIRMED

    metadata                       JSONB,
    version                        BIGINT       NOT NULL DEFAULT 0,                              -- optimistic lock (@Version)
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at                        TIMESTAMPTZ,
    expired_at                     TIMESTAMPTZ,
    CONSTRAINT ux_payments_idem UNIQUE (idempotency_key),
    CONSTRAINT ck_platform_fee_source
        CHECK (platform_fee_config_id IS NULL OR user_fee_override_id IS NULL),
    CONSTRAINT ck_payment_amounts CHECK (
        total_charged_amount = gross_amount + pg_fee_amount
            AND net_creator_amount = gross_amount - platform_fee_amount
            AND expected_settlement_amount = gross_amount
        )
);
CREATE INDEX ix_payments_creator ON payment.payments (user_id, id DESC);
CREATE INDEX ix_payments_status ON payment.payments (status, id DESC);
CREATE INDEX ix_payments_created ON payment.payments (created_at DESC);
-- Untuk job OVERDUE: payment PAID yang belum masuk batch settlement & tanggalnya lewat.
CREATE INDEX ix_payments_unsettled
    ON payment.payments (status, expected_settlement_date)
    WHERE status = 'PAID' AND settlement_id IS NULL;

-- Interaksi konkret ke 1 PG. Satu payment bisa retry/expire -> banyak attempt.
-- Reuse attempt AKTIF supaya pembayar balik ke tab tetap lihat VA/QRIS yang sama.
-- `provider_reference_id` = order_id yang kita kirim ke PG (UUID v7 dari attempt),
-- sekaligus kunci matching CSV/report settlement.
CREATE TABLE payment.payment_attempts
(
    id                       UUID PRIMARY KEY,
    payment_id               UUID        NOT NULL REFERENCES payment.payments (id),
    channel_route_id         BIGINT      NOT NULL REFERENCES payment.channel_routes (id),
    provider_reference_id    VARCHAR(120),         -- order_id dari sisi PG (dipakai matching webhook & CSV)
    payment_reference_number VARCHAR(120),         -- nomor VA / RRN / link yang ditampilkan ke PEMBAYAR
    status                   VARCHAR(20) NOT NULL, -- INITIATED | PENDING | PAID | EXPIRED | FAILED
    expires_at               TIMESTAMPTZ,          -- kedaluwarsa attempt; lewat ini boleh bikin baru
    raw_request              JSONB,
    raw_response             JSONB,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_attempts_provider_ref UNIQUE (channel_route_id, provider_reference_id)
);
CREATE INDEX ix_attempts_payment ON payment.payment_attempts (payment_id);
CREATE INDEX ix_attempts_ref_number
    ON payment.payment_attempts (payment_reference_number)
    WHERE payment_reference_number IS NOT NULL;
CREATE UNIQUE INDEX ux_attempts_active
    ON payment.payment_attempts (payment_id, channel_route_id)
    WHERE status IN ('INITIATED', 'PENDING');

-- =====================================================================
-- PAYMENT — refund (bisa sebagian, bisa banyak per payment)
-- =====================================================================

CREATE TABLE payment.refunds
(
    id                    UUID PRIMARY KEY,
    payment_id            UUID        NOT NULL REFERENCES payment.payments (id),
    provider_id           BIGINT      NOT NULL REFERENCES payment.providers (id),
    status                VARCHAR(20) NOT NULL,                    -- REQUESTED | PROCESSING | SUCCEEDED | FAILED
    amount                BIGINT      NOT NULL CHECK (amount > 0),
    reason                VARCHAR(240),
    provider_reference_id VARCHAR(120),
    raw_payload           JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,
    CONSTRAINT ux_refunds_provider_ref UNIQUE (provider_id, provider_reference_id)
);
CREATE INDEX ix_refunds_payment ON payment.refunds (payment_id, id DESC);

-- =====================================================================
-- PAYMENT — withdrawal & payout
-- =====================================================================

-- Rekening tujuan milik creator. Saat withdraw, datanya di-SNAPSHOT ke withdrawals.
CREATE TABLE payment.payout_destinations
(
    id             UUID PRIMARY KEY,
    user_id        UUID         NOT NULL,
    channel_id     BIGINT       NOT NULL REFERENCES payment.channels (id),
    account_number VARCHAR(40)  NOT NULL,
    account_name   VARCHAR(120) NOT NULL,
    bank_code      VARCHAR(20),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_payout_destinations_creator ON payment.payout_destinations (user_id);

-- Permintaan tarik dana creator. Saat dibuat: saldo available di-hold (jurnal HOLD).
CREATE TABLE payment.withdrawals
(
    id                         UUID PRIMARY KEY,
    idempotency_key            VARCHAR(160) NOT NULL,
    user_id                    UUID         NOT NULL,
    destination_id             UUID         NOT NULL REFERENCES payment.payout_destinations (id),
    channel_route_id           BIGINT       NOT NULL REFERENCES payment.channel_routes (id),
    status                     VARCHAR(20)  NOT NULL, -- REQUESTED | PROCESSING | PAID | FAILED | CANCELLED | REJECTED
    requested_amount           BIGINT       NOT NULL CHECK (requested_amount > 0),
    withdrawal_fee_amount      BIGINT       NOT NULL DEFAULT 0,
    net_disbursement_amount    BIGINT       NOT NULL,
    destination_account_number VARCHAR(40)  NOT NULL,
    destination_account_name   VARCHAR(120) NOT NULL,
    destination_bank_code      VARCHAR(20),
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at               TIMESTAMPTZ,
    CONSTRAINT ux_withdrawals_idem UNIQUE (idempotency_key),
    CONSTRAINT ck_withdrawal_net
        CHECK (net_disbursement_amount = requested_amount - withdrawal_fee_amount)
);
CREATE INDEX ix_withdrawals_creator ON payment.withdrawals (user_id, id DESC);
CREATE INDEX ix_withdrawals_status ON payment.withdrawals (status, id DESC);

-- Eksekusi disbursement ke payout provider. 1 withdrawal bisa retry (banyak payout).
CREATE TABLE payment.payouts
(
    id                    UUID PRIMARY KEY,
    withdrawal_id         UUID        NOT NULL REFERENCES payment.withdrawals (id),
    provider_id           BIGINT      NOT NULL REFERENCES payment.providers (id),
    status                VARCHAR(20) NOT NULL,           -- PENDING | COMPLETED | FAILED | REVERSED
    amount                BIGINT      NOT NULL,
    provider_fee_amount   BIGINT      NOT NULL DEFAULT 0,
    provider_reference_id VARCHAR(120),
    failure_code          VARCHAR(60),
    failure_reason        VARCHAR(240),
    raw_payload           JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,
    CONSTRAINT ux_payouts_provider_ref UNIQUE (provider_id, provider_reference_id)
);
CREATE INDEX ix_payouts_withdrawal ON payment.payouts (withdrawal_id);
CREATE INDEX ix_payouts_status ON payment.payouts (status, id DESC);

-- Perpindahan dana operasional antar akun platform (bukan revenue/expense).
CREATE TABLE payment.fund_transfers
(
    id                       UUID PRIMARY KEY,
    direction                VARCHAR(20) NOT NULL,                         -- TO_PAYOUT_PROVIDER | TO_BANK
    source_type              VARCHAR(20) NOT NULL,                         -- BANK | PROVIDER_BALANCE
    source_provider_id       BIGINT REFERENCES payment.providers (id),
    target_type              VARCHAR(20) NOT NULL,                         -- PAYOUT_PROVIDER | BANK
    counterparty_provider_id BIGINT REFERENCES payment.providers (id),
    sent_amount              BIGINT      NOT NULL CHECK (sent_amount > 0),
    fee_amount               BIGINT      NOT NULL DEFAULT 0,
    received_amount          BIGINT,
    variance_amount          BIGINT,
    status                   VARCHAR(20) NOT NULL,                         -- PENDING | IN_TRANSIT | COMPLETED | FAILED
    bank_reference           VARCHAR(120),
    provider_reference       VARCHAR(120),
    note                     VARCHAR(240),
    created_by               UUID,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ
);

-- Koreksi yang punya jejak & approval (maker-checker).
CREATE TABLE payment.adjustments
(
    id                 UUID PRIMARY KEY,
    scope              VARCHAR(20)  NOT NULL,                    -- FUND_TRANSFER | SETTLEMENT | PAYOUT | PAYMENT | MANUAL
    reference_type     VARCHAR(40),
    reference_id       VARCHAR(64),
    amount             BIGINT       NOT NULL CHECK (amount > 0),
    journal_id         BIGINT,
    reason             VARCHAR(300) NOT NULL,
    evidence_reference VARCHAR(200),
    requested_by       UUID,
    approved_by        UUID,
    status             VARCHAR(20)  NOT NULL,                    -- PENDING | POSTED | REJECTED
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    posted_at          TIMESTAMPTZ,
    CONSTRAINT ck_adjustment_approver CHECK (requested_by IS DISTINCT FROM approved_by)
);
CREATE INDEX ix_adjustments_ref ON payment.adjustments (reference_type, reference_id);

-- =====================================================================
-- PAYMENT — idempotency inbox
-- =====================================================================

-- "Inbox" webhook. Unique (provider, external_event_id) bikin event dobel di-skip.
-- provider_id WAJIB diisi (NULL tidak distinct di Postgres -> bisa lolos dobel).
CREATE TABLE payment.processed_events
(
    id                UUID PRIMARY KEY,
    provider_id       BIGINT       NOT NULL REFERENCES payment.providers (id),
    event_type        VARCHAR(60)  NOT NULL, -- PAYMENT_PAID | PAYMENT_EXPIRED | PAYMENT_FAILED
    -- | PAYMENT_DENIED | PAYMENT_CANCELLED
    -- | PAYOUT_COMPLETED | PAYOUT_FAILED | SETTLEMENT
    external_event_id VARCHAR(160) NOT NULL,
    payload           JSONB,
    processed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_processed_events UNIQUE (provider_id, external_event_id)
);

-- =====================================================================
-- PAYMENT — reconciliation
-- =====================================================================

CREATE TABLE payment.reconciliation_runs
(
    id           UUID PRIMARY KEY,
    type         VARCHAR(20) NOT NULL, -- PAYMENT | SETTLEMENT | PAYOUT | FUND_TRANSFER | LEDGER
    provider_id  BIGINT REFERENCES payment.providers (id),
    period_start TIMESTAMPTZ,
    period_end   TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL, -- RUNNING | COMPLETED | FAILED
    summary      JSONB,
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ
);

CREATE TABLE payment.reconciliation_items
(
    id                UUID PRIMARY KEY,
    run_id            UUID         NOT NULL REFERENCES payment.reconciliation_runs (id),
    match_key         VARCHAR(200) NOT NULL,
    internal_ref      VARCHAR(120),
    external_ref      VARCHAR(120),
    internal_amount   BIGINT,
    external_amount   BIGINT,
    status            VARCHAR(30)  NOT NULL, -- MATCHED | AMOUNT_MISMATCH | MISSING_INTERNAL | MISSING_EXTERNAL | RESOLVED
    resolution_action VARCHAR(60),
    resolved_by       UUID,
    resolved_at       TIMESTAMPTZ,
    note              VARCHAR(300),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_recon_items_run ON payment.reconciliation_items (run_id, status);
