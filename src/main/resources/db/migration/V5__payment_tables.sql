CREATE SCHEMA IF NOT EXISTS payment;


-- =====================================================================
-- PAYMENT — master & routing
-- =====================================================================

-- Master payment gateway / disbursement provider. Satu baris = satu vendor (Midtrans, Flip, ...).
CREATE TABLE payment.providers
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,               -- MIDTRANS | FLIP | DOKU | ...
    name            VARCHAR(100) NOT NULL,
    supports_payin  BOOLEAN      NOT NULL DEFAULT FALSE, -- bisa terima pembayaran?
    supports_payout BOOLEAN      NOT NULL DEFAULT FALSE, -- bisa kirim dana/withdrawal?
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_providers_code UNIQUE (code)
);

-- Seeder provider awal (idempoten).
INSERT INTO payment.providers (code, name, supports_payin, supports_payout)
VALUES ('MIDTRANS', 'Midtrans', TRUE, FALSE),
       ('FLIP', 'Flip', FALSE, TRUE)
ON CONFLICT ON CONSTRAINT ux_providers_code DO NOTHING;

-- Channel logis yang stabil & tidak terikat provider. FE/API merujuk ke sini, bukan ke PG.
CREATE TABLE payment.channels
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL, -- BCA_VA | QRIS | GOPAY | BANK_BCA
    display_name VARCHAR(120) NOT NULL, -- teks yang dilihat pembayar, mis. 'Transfer BCA Virtual Account'
    type         VARCHAR(20)  NOT NULL, -- VA | QRIS | EWALLET | BANK_TRANSFER | PAYOUT_BANK | PAYOUT_EWALLET
    direction    VARCHAR(10)  NOT NULL, -- PAYIN (masuk) | PAYOUT (keluar)
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_channels_code UNIQUE (code)
);

-- Seeder channel awal (idempoten).
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
-- Nambah PG/channel = insert baris, TANPA ubah kode atau tabel lain.
CREATE TABLE payment.channel_routes
(
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider_id           BIGINT      NOT NULL REFERENCES payment.providers (id),
    channel_id            BIGINT      NOT NULL REFERENCES payment.channels (id),
    provider_channel_code VARCHAR(60) NOT NULL,             -- kode spesifik milik PG utk channel ini
    min_amount            BIGINT      NOT NULL DEFAULT 0,   -- batas bawah nominal
    max_amount            BIGINT      NOT NULL DEFAULT 0,   -- 0 = tanpa batas atas
    priority              INT         NOT NULL DEFAULT 100, -- kecil = diprioritaskan saat failover
    is_active             BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_routes_provider_channel UNIQUE (provider_id, channel_id)
);

-- Seeder routing awal (idempoten, pakai subquery karena id provider/channel auto-generate).
INSERT INTO payment.channel_routes
(provider_id, channel_id, provider_channel_code, min_amount, max_amount, priority)
SELECT p.id AS provider_id,
       c.id AS channel_id,
       v.provider_channel_code,
       v.min_amount,
       v.max_amount,
       v.priority
FROM (VALUES
          -- PAYIN -> MIDTRANS
          ('MIDTRANS', 'VA_PERMATA', 'permata', 10000, 50000000, 100),
          ('MIDTRANS', 'VA_BCA', 'bca', 10000, 50000000, 100),
          ('MIDTRANS', 'VA_BNI', 'bni', 10000, 50000000, 100),
          ('MIDTRANS', 'VA_BRI', 'bri', 10000, 50000000, 100),
          ('MIDTRANS', 'VA_CIMB', 'cimb', 10000, 50000000, 100),
          ('MIDTRANS', 'QRIS', 'qris', 1000, 50000000, 100),

          -- PAYOUT -> Flip
          ('Flip', 'BANK_BCA', 'bca', 10000, 50000000, 100),
          ('Flip', 'BANK_BNI', 'bni', 10000, 50000000, 100),
          ('Flip', 'BANK_BRI', 'bri', 10000, 50000000, 100),
          ('Flip', 'BANK_CIMB', 'cimb', 10000, 50000000, 100),
          ('Flip', 'BANK_MANDIRI', 'mandiri', 10000, 50000000, 100),
          ('Flip', 'EWALLET_GOPAY', 'gopay', 10000, 50000000, 100),
          ('Flip', 'EWALLET_SHOPEE', 'shopeepay', 10000, 50000000, 100),
          ('Flip', 'EWALLET_OVO', 'ovo', 10000, 50000000, 100)) AS v(provider_code, channel_code, provider_channel_code,
                                                                     min_amount, max_amount, priority)
         JOIN payment.providers p ON p.code = v.provider_code
         JOIN payment.channels c ON c.code = v.channel_code
ON CONFLICT ON CONSTRAINT ux_routes_provider_channel DO NOTHING;

-- =====================================================================
-- PAYMENT — fee (versi)
-- =====================================================================

-- Rate card BERVERSI. Insert baris BARU tiap rate berubah; JANGAN update baris lama.
-- `effective_from` = kapan mulai berlaku; `effective_to` NULL = masih berlaku.
CREATE TABLE payment.fee_configs
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_type       VARCHAR(30) NOT NULL,                     -- PLATFORM_DONATION | PLATFORM_CONTENT
    -- | PLATFORM_WITHDRAWAL | GATEWAY_PROCESSING | PAYOUT
    provider_id    BIGINT REFERENCES payment.providers (id), -- diisi utk GATEWAY_PROCESSING/PAYOUT
    channel_id     BIGINT REFERENCES payment.channels (id),  -- diisi utk GATEWAY_PROCESSING/PAYOUT
    fixed_amount   BIGINT      NOT NULL DEFAULT 0,           -- nominal tetap (IDR)
    percentage_bps INT         NOT NULL DEFAULT 0,           -- persen dalam bps (500 = 5%)
    vat_bps        INT         NOT NULL DEFAULT 0,           -- PPN atas (fixed+percentage), 1100 = 11%
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to   TIMESTAMPTZ,                              -- NULL = berlaku terus
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

-- Seeder fee default (idempoten)
-- PLATFORM_*: provider_id & channel_id = NULL
-- GATEWAY_PROCESSING: MIDTRANS (PAYIN)
-- PAYOUT: Flip (PAYOUT)
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
          -- =========================================================================
          -- 1. PLATFORM FEES (Global - Tanpa Provider/Channel)
          -- =========================================================================
          ('PLATFORM_DONATION', NULL, NULL, 0, 500, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Platform fee donasi (5% + PPN 11%)'),
          ('PLATFORM_CONTENT', NULL, NULL, 0, 500, 1100, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Platform fee konten (5% + PPN 11%)'),
          ('PLATFORM_WITHDRAWAL', NULL, NULL, 3000, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Platform withdrawal fee flat Rp3.000'),

          -- =========================================================================
          -- 2. GATEWAY_PROCESSING (Midtrans - PAYIN Channels)
          -- =========================================================================
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

          -- =========================================================================
          -- 3. PAYOUT (Flip - PAYOUT Channels)
          -- =========================================================================
          ('PAYOUT', 'Flip', 'BANK_BCA', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank BCA payout (Rp2.500)'),
          ('PAYOUT', 'Flip', 'BANK_BNI', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank BNI payout (Rp2.500)'),
          ('PAYOUT', 'Flip', 'BANK_BRI', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank BRI payout (Rp2.500)'),
          ('PAYOUT', 'Flip', 'BANK_CIMB', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip Bank CIMB payout (Rp2.500)'),
          ('PAYOUT', 'Flip', 'EWALLET_GOPAY', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip GoPay payout (Rp2.500)'),
          ('PAYOUT', 'Flip', 'EWALLET_SHOPEE', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
           'Flip ShopeePay payout (Rp2.500)'),
          ('PAYOUT', 'Flip', 'EWALLET_OVO', 2500, 0, 0, TIMESTAMPTZ '2026-10-03 00:00:00+07',
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
-- Insert baris BARU tiap perubahan; ada `reason` + `approved_by` untuk audit.
CREATE TABLE payment.user_fee_overrides
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        UUID         NOT NULL,
    fee_type       VARCHAR(30)  NOT NULL, -- PLATFORM_DONATION | PLATFORM_CONTENT
    -- | PLATFORM_WITHDRAWAL
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
-- PAYMENT — transaksi & attempt
-- =====================================================================

-- Niat transaksi bisnis (donasi/content) + SNAPSHOT fee. 1 payment bisa punya banyak attempt.
-- Fee TIDAK dihitung ulang saat webhook; semua komponen disimpan di sini untuk audit.
CREATE TABLE payment.payments
(
    id                             UUID PRIMARY KEY,                                             -- UUID v7, id yang tampil ke user
    idempotency_key                VARCHAR(160) NOT NULL,                                        -- kunci cegah donasi dobel dari 1 request
    type                           VARCHAR(20)  NOT NULL,                                        -- DONATION | CONTENT_PURCHASE
    status                         VARCHAR(20)  NOT NULL,                                        -- INITIATED | PENDING | PAID | EXPIRED
    -- | FAILED | CANCELLED
    -- | PARTIALLY_REFUNDED | REFUNDED
    user_id                        UUID         NOT NULL,                                        -- user CREATOR PENERIMA hak (payee); identity.users.id
    payer_id                       UUID,                                                         -- user PEMBAYAR; NULL = anonim; identity.users.id
    channel_id                     BIGINT       NOT NULL REFERENCES payment.channels (id),       -- channel logis
    channel_route_id               BIGINT       NOT NULL REFERENCES payment.channel_routes (id), -- PG terpilih (snapshot)
    gross_amount                   BIGINT       NOT NULL CHECK (gross_amount > 0),               -- nominal donasi murni

    -- snapshot fee PG (pass-through; tidak masuk ledger, hanya untuk rekonsiliasi)
    gateway_fee_config_id          BIGINT REFERENCES payment.fee_configs (id),                   -- config PG yang dipakai
    pg_fixed_fee_amount            BIGINT       NOT NULL DEFAULT 0,
    pg_percentage_fee_bps          INT          NOT NULL DEFAULT 0,
    pg_percentage_fee_amount       BIGINT       NOT NULL DEFAULT 0,
    pg_vat_bps                     INT          NOT NULL DEFAULT 0,
    pg_vat_amount                  BIGINT       NOT NULL DEFAULT 0,
    pg_fee_amount                  BIGINT       NOT NULL DEFAULT 0,                              -- total = fixed + pct + vat

    -- snapshot fee platform
    platform_fee_config_id         BIGINT REFERENCES payment.fee_configs (id),                   -- dipakai kalau tanpa override
    user_fee_override_id           BIGINT REFERENCES payment.user_fee_overrides (id),            -- menang kalau ada
    platform_fixed_fee_amount      BIGINT       NOT NULL DEFAULT 0,
    platform_percentage_fee_bps    INT          NOT NULL DEFAULT 0,
    platform_percentage_fee_amount BIGINT       NOT NULL DEFAULT 0,
    platform_vat_bps               INT          NOT NULL DEFAULT 0,
    platform_vat_amount            BIGINT       NOT NULL DEFAULT 0,
    platform_fee_amount            BIGINT       NOT NULL DEFAULT 0,                              -- total = fixed + pct + vat

    total_charged_amount           BIGINT       NOT NULL,                                        -- dibayar pembayar (gross + pg fee)
    net_creator_amount             BIGINT       NOT NULL,                                        -- hak creator (gross - platform fee)
    expected_settlement_amount     BIGINT       NOT NULL,                                        -- uang yang diharap settle dari PG

    metadata                       JSONB,                                                        -- data bebas (response PG, ...)
    version                        BIGINT       NOT NULL DEFAULT 0,                              -- optimistic lock
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    paid_at                        TIMESTAMPTZ,                                                  -- diisi saat webhook PAID
    expired_at                     TIMESTAMPTZ,                                                  -- diisi saat expired/cancelled
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

-- Interaksi konkret ke 1 PG. Satu payment bisa retry/expire → banyak attempt.
-- Reuse attempt AKTIF supaya pembayar balik ke tab tetap lihat VA/QRIS yang sama.
CREATE TABLE payment.payment_attempts
(
    id                       UUID PRIMARY KEY,
    payment_id               UUID        NOT NULL REFERENCES payment.payments (id),
    channel_route_id         BIGINT      NOT NULL REFERENCES payment.channel_routes (id),
    provider_reference_id    VARCHAR(120),         -- id/order_id dari sisi PG (dipakai matching webhook)
    payment_reference_number VARCHAR(120),         -- nomor VA / RRN / link yang ditampilkan ke PEMBAYAR
    status                   VARCHAR(20) NOT NULL, -- INITIATED | PENDING | PAID | EXPIRED | FAILED
    expires_at               TIMESTAMPTZ,          -- kedaluwarsa attempt; lewat ini boleh bikin baru
    raw_request              JSONB,                -- request kita ke PG (bukti audit)
    raw_response             JSONB,                -- response PG (bukti audit)
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_attempts_provider_ref UNIQUE (channel_route_id, provider_reference_id)
);
CREATE INDEX ix_attempts_payment ON payment.payment_attempts (payment_id);
CREATE INDEX ix_attempts_ref_number
    ON payment.payment_attempts (payment_reference_number)
    WHERE payment_reference_number IS NOT NULL;
-- Menjamin 1 payment hanya punya 1 attempt AKTIF per route; untuk reuse VA/QRIS saat
-- pembayar balik tanpa bikin attempt baru. Attempt lama yang sudah EXPIRED boleh punya baru.
CREATE UNIQUE INDEX ux_attempts_active
    ON payment.payment_attempts (payment_id, channel_route_id)
    WHERE status IN ('INITIATED', 'PENDING');

-- =====================================================================
-- PAYMENT — settlement (status terpisah dari payment)
-- =====================================================================

-- Ekspektasi + BUKTI settlement. Terpisah dari status payment.
-- `expected_*` hanya estimasi; status baru CONFIRMED setelah ada `evidence_*`.
CREATE TABLE payment.settlements
(
    id                       UUID PRIMARY KEY,
    payment_id               UUID        NOT NULL REFERENCES payment.payments (id), -- 1 payment = 1 settlement
    provider_id              BIGINT      NOT NULL REFERENCES payment.providers (id),
    status                   VARCHAR(20) NOT NULL,                                  -- EXPECTED | OVERDUE | CONFIRMED | CANCELLED
    expected_amount          BIGINT      NOT NULL,                                  -- net yang kita HARAP diterima
    expected_settlement_date DATE        NOT NULL,                                  -- ESTIMASI T+n, bukan bukti; hanya pemicu pengecekan
    settlement_target        VARCHAR(20),                                           -- BANK | PROVIDER_BALANCE (default: PROVIDER_BALANCE)
    actual_amount            BIGINT,                                                -- jumlah yang BENAR-BENAR masuk (dari bukti)
    actual_settled_at        TIMESTAMPTZ,
    variance_amount          BIGINT,                                                -- = actual_amount - expected_amount
    --   >0 kelebihan, <0 kekurangan (mis. potongan fee tambahan)
    external_settlement_id   VARCHAR(120),                                          -- id batch/settlement dari PG (kalau ada)
    evidence_source          VARCHAR(30),                                           -- API | REPORT_FILE | BANK_STATEMENT | MANUAL
    evidence_reference       VARCHAR(200),
    raw_evidence             JSONB,
    confirmed_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_settlements_payment UNIQUE (payment_id)
);
CREATE INDEX ix_settlements_due ON payment.settlements (status, expected_settlement_date);
CREATE INDEX ix_settlements_external
    ON payment.settlements (provider_id, external_settlement_id)
    WHERE external_settlement_id IS NOT NULL;

-- =====================================================================
-- PAYMENT — refund (bisa sebagian, bisa banyak per payment)
-- =====================================================================

-- Refund penuh/sebagian. Boleh banyak baris per payment (partial refund).
-- Diproses sebagai jurnal baru (koreksi), BUKAN edit jurnal lama.
CREATE TABLE payment.refunds
(
    id                    UUID PRIMARY KEY,
    payment_id            UUID        NOT NULL REFERENCES payment.payments (id),
    provider_id           BIGINT      NOT NULL REFERENCES payment.providers (id),
    status                VARCHAR(20) NOT NULL,                    -- REQUESTED | PROCESSING | SUCCEEDED | FAILED
    amount                BIGINT      NOT NULL CHECK (amount > 0), -- nominal refund (<= total dibayar)
    reason                VARCHAR(240),
    provider_reference_id VARCHAR(120),                            -- id refund dari PG
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
    user_id        UUID         NOT NULL,                                  -- pemilik rekening (identity.users.id)
    channel_id     BIGINT       NOT NULL REFERENCES payment.channels (id), -- channel payout (mis. BANK_BCA)
    account_number VARCHAR(40)  NOT NULL,
    account_name   VARCHAR(120) NOT NULL,
    bank_code      VARCHAR(20),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_payout_destinations_creator ON payment.payout_destinations (user_id);

-- Permintaan tarik dana creator. Saat dibuat: saldo available di-hold (lihat jurnal HOLD).
CREATE TABLE payment.withdrawals
(
    id                         UUID PRIMARY KEY,
    idempotency_key            VARCHAR(160) NOT NULL,                                        -- cegah double request
    user_id                    UUID         NOT NULL,
    destination_id             UUID         NOT NULL REFERENCES payment.payout_destinations (id),
    channel_route_id           BIGINT       NOT NULL REFERENCES payment.channel_routes (id), -- PG payout terpilih
    status                     VARCHAR(20)  NOT NULL,                                        -- REQUESTED | PROCESSING | PAID
    -- | FAILED | CANCELLED | REJECTED
    requested_amount           BIGINT       NOT NULL CHECK (requested_amount > 0),           -- dipotong dari available
    withdrawal_fee_amount      BIGINT       NOT NULL DEFAULT 0,                              -- fee platform
    net_disbursement_amount    BIGINT       NOT NULL,                                        -- yang diterima creator = requested - fee
    destination_account_number VARCHAR(40)  NOT NULL,                                        -- snapshot tujuan
    destination_account_name   VARCHAR(120) NOT NULL,                                        -- snapshot tujuan
    destination_bank_code      VARCHAR(20),                                                  -- snapshot tujuan
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
    amount                BIGINT      NOT NULL,           -- nominal yang MASUK ke rekening creator (net)
    provider_fee_amount   BIGINT      NOT NULL DEFAULT 0, -- fee provider; float berkurang = amount + fee
    provider_reference_id VARCHAR(120),                   -- id disbursement dari provider (matching webhook/laporan)
    failure_code          VARCHAR(60),                    -- kode gagal dari provider (mis. ACCOUNT_INVALID)
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
-- Menyatukan 2 leg transfer: keluar dari sumber (bank/saldo provider) -> masuk ke tujuan.
-- `variance_amount = sent_amount - fee_amount - received_amount`; !=0 artinya perlu adjustment.
CREATE TABLE payment.fund_transfers
(
    id                       UUID PRIMARY KEY,
    direction                VARCHAR(20) NOT NULL,                         -- TO_PAYOUT_PROVIDER | TO_BANK
    source_type              VARCHAR(20) NOT NULL,                         -- BANK | PROVIDER_BALANCE (darimana dana keluar)
    source_provider_id       BIGINT REFERENCES payment.providers (id),     -- diisi bila source_type=PROVIDER_BALANCE
    target_type              VARCHAR(20) NOT NULL,                         -- PAYOUT_PROVIDER | BANK
    counterparty_provider_id BIGINT REFERENCES payment.providers (id),     -- provider tujuan (kalau ke provider)
    sent_amount              BIGINT      NOT NULL CHECK (sent_amount > 0), -- nominal keluar dari sumber
    fee_amount               BIGINT      NOT NULL DEFAULT 0,               -- biaya transfer bank/admin yang terdokumentasi
    received_amount          BIGINT,                                       -- nominal yang benar-benar masuk tujuan
    variance_amount          BIGINT,                                       -- sent - fee - received (0 = pas)
    status                   VARCHAR(20) NOT NULL,                         -- PENDING | IN_TRANSIT | COMPLETED | FAILED
    bank_reference           VARCHAR(120),                                 -- ref mutasi di sisi BANK (bukti leg keluar/masuk)
    provider_reference       VARCHAR(120),                                 -- ref top-up/penarikan di sisi PROVIDER (bukti leg masuk)
    note                     VARCHAR(240),
    created_by               UUID,                                         -- admin/operator yang menjalankan (bila manual)
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at             TIMESTAMPTZ
);

-- Koreksi yang punya jejak & approval. Tiap baris diposting sebagai 1 journal ADJUSTMENT.
-- `journal_id` = pointer ke ledger.journals (tanpa FK lintas modul). Wajib ada `reason`.
CREATE TABLE payment.adjustments
(
    id                 UUID PRIMARY KEY,
    scope              VARCHAR(20)  NOT NULL,                    -- FUND_TRANSFER | SETTLEMENT | PAYOUT | PAYMENT | MANUAL
    reference_type     VARCHAR(40),                              -- sumber yang dikoreksi (opsional), mis. FUND_TRANSFER
    reference_id       VARCHAR(64),                              -- id sumber (mis. FT-1)
    amount             BIGINT       NOT NULL CHECK (amount > 0), -- nominal koreksi
    journal_id         BIGINT,                                   -- id jurnal ADJUSTMENT yang diposting
    reason             VARCHAR(300) NOT NULL,                    -- kenapa dikoreksi (wajib, untuk audit)
    evidence_reference VARCHAR(200),                             -- bukti pendukung (nomor tiket, link file)
    requested_by       UUID,
    approved_by        UUID,                                     -- beda orang dari requested_by (segregation of duty)
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
CREATE TABLE payment.processed_events
(
    id                UUID PRIMARY KEY,
    provider_id       BIGINT REFERENCES payment.providers (id),
    event_type        VARCHAR(60)  NOT NULL, -- PAYMENT_PAID | PAYMENT_EXPIRED | PAYMENT_FAILED
    -- | PAYOUT_COMPLETED | PAYOUT_FAILED | SETTLEMENT
    external_event_id VARCHAR(160) NOT NULL, -- event id dari PG; kalau tidak ada, pakai hash deterministik
    payload           JSONB,
    processed_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_processed_events UNIQUE (provider_id, external_event_id)
);

-- =====================================================================
-- PAYMENT — reconciliation
-- =====================================================================

-- Header 1 kali proses rekonsiliasi (opsional; bisa dipakai untuk audit proses).
CREATE TABLE payment.reconciliation_runs
(
    id           UUID PRIMARY KEY,
    type         VARCHAR(20) NOT NULL, -- PAYMENT | SETTLEMENT | PAYOUT | FUND_TRANSFER | LEDGER
    provider_id  BIGINT REFERENCES payment.providers (id),
    period_start TIMESTAMPTZ,          -- periode data yang dicek
    period_end   TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL, -- RUNNING | COMPLETED | FAILED
    summary      JSONB,                -- ringkasan temuan (jumlah match/mismatch)
    started_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at  TIMESTAMPTZ
);

-- Temuan per baris hasil membandingkan internal vs eksternal.
CREATE TABLE payment.reconciliation_items
(
    id                UUID PRIMARY KEY,
    run_id            UUID         NOT NULL REFERENCES payment.reconciliation_runs (id),
    match_key         VARCHAR(200) NOT NULL, -- kunci pencocokan (mis. provider_reference_id)
    internal_ref      VARCHAR(120),          -- id di sistem kita
    external_ref      VARCHAR(120),          -- id di PG/bank
    internal_amount   BIGINT,                -- nominal menurut kita
    external_amount   BIGINT,                -- nominal menurut eksternal
    status            VARCHAR(30)  NOT NULL, -- MATCHED | AMOUNT_MISMATCH
    -- | MISSING_INTERNAL | MISSING_EXTERNAL | RESOLVED
    resolution_action VARCHAR(60),           -- ADJUSTMENT_POSTED | MANUAL_CORRECTION
    -- | IGNORED | RETRY | ESCALATED
    resolved_by       UUID,
    resolved_at       TIMESTAMPTZ,
    note              VARCHAR(300),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_recon_items_run ON payment.reconciliation_items (run_id, status);

-- =====================================================================
-- SEEDER — Chart of Accounts
-- Kontrol (owner NULL) di-seed sekarang; sub-account provider mengikuti provider.
-- Sub-account creator (2100/2110/2200/5300) dibuat LAZY saat creator pertama dapat hak.
-- =====================================================================

-- Akun kontrol global (tanpa owner).
INSERT INTO ledger.accounts (code, name, type, normal_balance, owner_type, owner_ref)
VALUES ('2300', 'VAT Payable', 'LIABILITY', 'CREDIT', NULL, NULL),
       ('4000', 'Platform Fee Revenue', 'REVENUE', 'CREDIT', NULL, NULL),
       ('4100', 'Withdrawal Fee Revenue', 'REVENUE', 'CREDIT', NULL, NULL),
       ('5000', 'Payment Gateway Fee Expense', 'EXPENSE', 'DEBIT', NULL, NULL),
       ('5100', 'Payout Fee Expense', 'EXPENSE', 'DEBIT', NULL, NULL),
       ('5200', 'Refund / Chargeback Loss', 'EXPENSE', 'DEBIT', NULL, NULL)
ON CONFLICT ON CONSTRAINT ux_accounts_code_owner DO NOTHING;

-- Akun clearing per provider payin (1100 + 1150).
INSERT INTO ledger.accounts (code, name, type, normal_balance, owner_type, owner_ref)
SELECT v.code, v.name, 'ASSET', 'DEBIT', 'PAYMENT_PROVIDER', p.id::text
FROM (VALUES ('1100', 'PG Clearing Receivable'),
             ('1150', 'Payin Provider Balance')) AS v(code, name)
         CROSS JOIN payment.providers p
WHERE p.supports_payin
ON CONFLICT ON CONSTRAINT ux_accounts_code_owner DO NOTHING;

-- Akun float per provider payout (1300).
INSERT INTO ledger.accounts (code, name, type, normal_balance, owner_type, owner_ref)
SELECT '1300', 'Payout Provider Float', 'ASSET', 'DEBIT', 'PAYOUT_PROVIDER', p.id::text
FROM payment.providers p
WHERE p.supports_payout
ON CONFLICT ON CONSTRAINT ux_accounts_code_owner DO NOTHING;