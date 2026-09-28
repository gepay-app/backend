-- =====================================================================
-- GEPAY - PAYMENT MODULE SCHEMA (schema: payment)
-- Prinsip utama:
--   1. Ledger (ledger_entries) = SUMBER KEBENARAN, append-only, tidak pernah di-UPDATE/DELETE.
--   2. Wallet.balance = cache, harus selalu bisa direkonsiliasi dari SUM(ledger_entries).
--   3. Semua rate/fee di-snapshot ke transaction saat create, TIDAK pernah dihitung ulang
--      saat webhook masuk. Perubahan config di masa depan tidak boleh mengubah transaksi lama.
--   4. Semua uang: BIGINT, satuan terkecil (rupiah penuh, integer, tidak pernah FLOAT).
--   5. Semua rate: INT dalam basis points (bps). 1 bps = 0.01%. 10000 bps = 100%.
--      Alasan pilih bps ketimbang NUMERIC: konsisten dengan prinsip "semua angka finansial
--      = integer", dan tidak ambigu saat diserialisasi lintas bahasa/service (JSON dll).
-- =====================================================================


-- ============================ MASTER DATA ============================

CREATE TABLE payment.payment_gateways
(
    id         UUID PRIMARY KEY,
    code       VARCHAR(30)  NOT NULL UNIQUE, -- 'XENDIT' | 'MIDTRANS' -- kode internal PG
    name       VARCHAR(100) NOT NULL,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE payment.channels
(
    id           UUID PRIMARY KEY,
    -- Kode LOGIS internal, dipakai FE/API, stabil selamanya, TIDAK terikat 1 PG tertentu.
    -- Contoh: 'BCA_VA'. FE/donatur tidak pernah tahu/peduli PG mana di baliknya.
    code         VARCHAR(30)  NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL, -- 'Transfer BCA Virtual Account'
    type         VARCHAR(20)  NOT NULL, -- 'VA' | 'EWALLET' | 'QRIS' | 'BANK_TRANSFER' | 'EWALLET_PAYOUT'
    -- 'INBOUND'  -> dipakai untuk terima donasi (VA/QRIS/EWALLET saat bayar)
    -- 'OUTBOUND' -> dipakai untuk withdrawal/disbursement (BANK_TRANSFER/EWALLET_PAYOUT)
    direction    VARCHAR(10)  NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE
);

-- Tabel ROUTING: jantung dari dukungan multi-PG.
-- 1 channel logis bisa dilayani lebih dari 1 gateway, tiap gateway punya kode & fee sendiri.
-- Nambah PG baru / channel baru = insert row baru di sini, NOL perubahan kode/migrasi tabel lain.
CREATE TABLE payment.gateway_channel_configs
(
    id                   UUID PRIMARY KEY,
    gateway_id           UUID        NOT NULL REFERENCES payment.payment_gateways (id),
    channel_id           UUID        NOT NULL REFERENCES payment.channels (id),

    -- Kode SPESIFIK milik gateway ybs untuk channel ini.
    -- Contoh: Xendit pakai 'VA_BCA', Midtrans pakai 'BCA' -- untuk channel logis yang sama 'BCA_VA'.
    gateway_channel_code VARCHAR(50) NOT NULL,

    fee_fixed_bps        BIGINT      NOT NULL DEFAULT 0,    -- fee fixed dalam rupiah (bukan bps, nominal tetap)
    fee_percentage_bps   INT         NOT NULL DEFAULT 0,    -- fee persentase PG, dalam bps (70 = 0.7%)
    vat_percentage_bps   INT         NOT NULL DEFAULT 1100, -- PPN atas fee PG, dalam bps (1100 = 11%)

    -- Batas nominal yang boleh lewat kombinasi PG+channel ini. min_amount wajib untuk
    -- OUTBOUND (bank/e-wallet payout hampir selalu ada minimum, umumnya Rp10.000).
    -- max_amount 0 berarti "tidak ada batas atas".
    min_amount           BIGINT      NOT NULL DEFAULT 0,
    max_amount           BIGINT      NOT NULL DEFAULT 0,

    -- Dipakai untuk routing/failover: makin kecil makin diprioritaskan saat pilih PG untuk channel ini.
    priority             INT         NOT NULL DEFAULT 100,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,

    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    UNIQUE (gateway_id, channel_id)
);

-- Fee Gepay sendiri (REVENUE), sengaja dipisah dari gateway_channel_configs (COST ke vendor)
-- karena alasan berubahnya beda: ini murni keputusan bisnis Gepay, tidak terikat channel/PG.
CREATE TABLE payment.platform_fee_rules
(
    id                 UUID PRIMARY KEY,
    -- 'DONATION'   -> potongan dari nominal donasi (saat ini 5%)
    -- 'WITHDRAWAL' -> potongan dari withdrawal, default 0 tapi disiapkan kalau nanti mau dipakai
    applies_to         VARCHAR(20) NOT NULL,
    fee_fixed_amount   BIGINT      NOT NULL DEFAULT 0, -- rupiah, fixed
    fee_percentage_bps INT         NOT NULL DEFAULT 0, -- bps (500 = 5%)
    is_active          BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Insert baris BARU tiap kali rate berubah, JANGAN UPDATE baris lama.
    -- Ini yang membuat histori perubahan rate Gepay sendiri tetap auditable.
    effective_from     TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);

-- Fee spesial per-user, override dari platform_fee_rules global. Dicek LEBIH DULU sebelum
-- pakai rate global: kalau ada override aktif untuk user & applies_to yang cocok, pakai ini;
-- kalau tidak ada, fallback ke platform_fee_rules yang aktif saat itu.
CREATE TABLE payment.user_fee_overrides
(
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL, -- identity.users.id, tanpa FK lintas schema (batas modul)
    applies_to         VARCHAR(20)  NOT NULL, -- 'DONATION' | 'WITHDRAWAL'
    fee_fixed_amount   BIGINT       NOT NULL DEFAULT 0,
    fee_percentage_bps INT          NOT NULL DEFAULT 0,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Insert baris BARU tiap kali rate user ini berubah, JANGAN UPDATE baris lama (sama
    -- alasannya dengan platform_fee_rules -- histori rate khusus tetap auditable).
    effective_from     TIMESTAMPTZ  NOT NULL,
    reason             VARCHAR(500) NOT NULL, -- wajib: kenapa user ini dapat rate khusus
    approved_by        UUID         NOT NULL, -- admin yang menyetujui (identity.users.id)
    created_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ON payment.user_fee_overrides (user_id, applies_to, is_active);


-- ============================ WALLET & LEDGER ============================

CREATE TABLE payment.wallets
(
    id                UUID PRIMARY KEY,
    -- 'USER'          -> 1 baris per user (creator/streamer), owner_id wajib diisi
    -- 'PLATFORM_FEE'  -> 1 baris TUNGGAL di seluruh sistem, owner_id NULL, representasi revenue Gepay
    -- 'PG_CLEARING'   -> 1 baris PER payment gateway (owner_id = payment_gateways.id), representasi
    --                     "uang nangkring di PG tsb, belum settle ke rekening bank platform".
    --                     WAJIB per-gateway (bukan 1 baris global) supaya bisa direkonsiliasi
    --                     terpisah ke laporan settlement masing-masing PG (Xendit vs Midtrans
    --                     punya laporan sendiri-sendiri, tidak boleh dicampur).
    owner_type        VARCHAR(20) NOT NULL,
    owner_id          UUID,                           -- NULL hanya untuk PLATFORM_FEE

    balance           BIGINT      NOT NULL DEFAULT 0, -- TOTAL saldo, cache dari SUM(ledger_entries)
    pending_balance   BIGINT      NOT NULL DEFAULT 0, -- subset dari balance yang BELUM settle dari PG

    -- available = balance - pending. Dihitung otomatis oleh DB, bukan disimpan manual,
    -- supaya tidak pernah out-of-sync. Withdraw HARUS validasi ke kolom ini.
    available_balance BIGINT GENERATED ALWAYS AS (balance - pending_balance) STORED,

    -- Optimistic locking (jaring pengaman kedua setelah SELECT ... FOR UPDATE).
    -- BIGINT, bukan INT: baris panas (PG_CLEARING, PLATFORM_FEE) diupdate tiap donasi, INT bisa
    -- penuh dalam hitungan tahun di traffic tinggi, dan begitu penuh SEMUA transaksi ke wallet itu gagal.
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (owner_type, owner_id)
);

CREATE TABLE payment.journals
(
    id             UUID PRIMARY KEY,
    -- Free text untuk manusia, konteks 1 kejadian bisnis. Bukan pengganti entry_type
    -- (description tidak bisa diandalkan untuk agregasi laporan keuangan).
    description    VARCHAR(200) NOT NULL,

    -- Pointer generik ke record SUMBER kejadian ini. Ditaruh di journal (bukan di tiap
    -- ledger_entries) karena 1 journal = 1 kejadian = selalu 1 sumber yang sama untuk
    -- semua baris ledger di dalamnya -- taruh di ledger_entries akan redundant.
    -- 'TRANSACTION' | 'MANUAL_ADJUSTMENT' (masa depan) | dst.
    reference_type VARCHAR(30)  NOT NULL,
    reference_id   UUID         NOT NULL, -- transactions.id kalau reference_type='TRANSACTION'

    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ON payment.journals (reference_type, reference_id);

CREATE TABLE payment.ledger_entries
(
    id             UUID PRIMARY KEY,     -- UUID v7, time-ordered
    journal_id     UUID        NOT NULL REFERENCES payment.journals (id),
    wallet_id      UUID        NOT NULL REFERENCES payment.wallets (id),

    direction      VARCHAR(10) NOT NULL, -- 'DEBIT' | 'CREDIT'
    amount         BIGINT      NOT NULL CHECK (amount > 0),

    -- Snapshot saldo sebelum & sesudah baris ini. Membuat 1 baris self-contained untuk
    -- audit (tidak perlu query/LAG baris sebelumnya), dan berfungsi seperti hash-chain:
    -- balance_after baris ini harus match balance_before baris berikutnya di wallet yang sama.
    balance_before BIGINT      NOT NULL,
    balance_after  BIGINT      NOT NULL,

    -- Kategori akuntansi baris ini (mirip "chart of accounts"), untuk agregasi laporan,
    -- misal: SUM(amount) WHERE entry_type='PLATFORM_FEE_REVENUE' -> total revenue Gepay.
    -- 'GATEWAY_INFLOW' | 'DONATION_IN' | 'PLATFORM_FEE_REVENUE'
    -- | 'WITHDRAWAL' | 'WITHDRAWAL_REVERSAL' | 'REFUND' | 'ADJUSTMENT'
    -- Catatan: 'PG_FEE_COST' TIDAK dipakai di flow normal -- fee PG pass-through ke
    -- donatur/streamer, tidak pernah jadi cost ledger platform (lihat transactions.pg_fee_amount).
    entry_type     VARCHAR(30) NOT NULL,

    created_at     TIMESTAMPTZ NOT NULL
    -- TIDAK ADA UPDATE/DELETE privilege untuk role aplikasi di tabel ini (REVOKE di level DB).
    -- Salah catat -> buat entry BARU yang mengoreksi, jangan pernah edit baris lama.
);
-- Query paling umum: "riwayat mutasi 1 wallet, terbaru dulu" -- pakai id (UUID v7) bukan
-- created_at, alasan sama seperti transactions di atas (index lebih kecil, urutan sama).
CREATE INDEX idx_ledger_entries_wallet_id ON payment.ledger_entries (wallet_id, id DESC);


-- ============================ TRANSACTION ============================

CREATE TABLE payment.transactions
(
    id                             UUID PRIMARY KEY,
    type                           VARCHAR(20)  NOT NULL,                                                 -- 'DONATION_PAYMENT' | 'WITHDRAWAL' | 'REFUND'
    status                         VARCHAR(20)  NOT NULL,                                                 -- 'PENDING' -> 'PAID' -> 'SETTLED' | 'FAILED' | 'EXPIRED'

    -- Hanya diisi kalau type='REFUND': menunjuk balik ke transaction donasi yang direfund.
    -- Refund TIDAK PERNAH mengubah transaction asli -- selalu row baru (append-only).
    refund_original_transaction_id UUID REFERENCES payment.transactions (id),

    channel_id                     UUID         NOT NULL REFERENCES payment.channels (id),                -- channel logis
    gateway_channel_config_id      UUID         NOT NULL REFERENCES payment.gateway_channel_configs (id), -- PG mana yg menang saat create (snapshot rate PG, min/max amount)

    -- Snapshot SUMBER fee platform yang dipakai saat create -- hanya salah satu yang diisi.
    -- Kalau user_fee_override_id terisi, berarti platform_fee_rule_id di-skip (override menang).
    -- Auditor bisa lacak persis kenapa fee user ini beda dari user lain, lewat kolom ini.
    platform_fee_rule_id           UUID REFERENCES payment.platform_fee_rules (id),
    user_fee_override_id           UUID REFERENCES payment.user_fee_overrides (id),

    -- Nominal "murni"/basis, TIDAK termasuk fee apapun:
    --   DONATION_PAYMENT -> nilai donasi (dasar hitung platform_fee)
    --   WITHDRAWAL        -> nominal yang DIMINTA streamer untuk diterima di bank/e-wallet
    -- WAJIB >= gateway_channel_configs.min_amount saat create (snapshot, dicek di service layer)
    gross_amount                   BIGINT       NOT NULL,

    -- ---- breakdown fee PG: PASS-THROUGH, bukan cost/revenue platform. Kolom ini murni
    -- informational (kuitansi, pembukuan PPN masukan) -- TIDAK pernah jadi ledger entry ke
    -- wallet PLATFORM_FEE. Selalu DITAMBAHKAN di atas gross_amount, ditanggung pihak yang
    -- memicu transaksi (donatur utk donasi, streamer utk withdrawal via potongan wallet).
    pg_fixed_fee_amount            BIGINT       NOT NULL DEFAULT 0,
    pg_percentage_fee_bps          INT          NOT NULL DEFAULT 0,
    pg_percentage_fee_amount       BIGINT       NOT NULL DEFAULT 0,                                       -- = gross_amount * pg_percentage_fee_bps / 10000
    pg_vat_amount                  BIGINT       NOT NULL DEFAULT 0,                                       -- PPN 11% dari (fixed + percentage)
    pg_fee_amount                  BIGINT       NOT NULL DEFAULT 0,                                       -- TOTAL = fixed + percentage_amount + vat

    -- ---- fee platform (REVENUE Gepay), snapshot dari platform_fee_rules yg aktif saat create ----
    -- DONATION_PAYMENT: dipotong DARI gross_amount (mengurangi net_amount streamer).
    -- WITHDRAWAL: DITAMBAHKAN DI ATAS gross_amount (menambah total yg didebit dari wallet), default 0.
    platform_fixed_fee_amount      BIGINT       NOT NULL DEFAULT 0,
    platform_percentage_fee_bps    INT          NOT NULL DEFAULT 0,
    platform_percentage_fee_amount BIGINT       NOT NULL DEFAULT 0,                                       -- = gross_amount * platform_percentage_fee_bps / 10000
    platform_fee_amount            BIGINT       NOT NULL DEFAULT 0,

    -- Total nominal yang berpindah di sisi PEMBAYAR/PENDEBIT:
    --   DONATION_PAYMENT -> yg BENAR2 dibayar donatur = gross_amount + pg_fee_amount
    --   WITHDRAWAL        -> yg BENAR2 didebit dari wallet streamer = gross_amount + pg_fee_amount + platform_fee_amount
    total_charged_amount           BIGINT       NOT NULL,

    -- Nominal yang berpindah di sisi PENERIMA:
    --   DONATION_PAYMENT -> masuk wallet STREAMER = gross_amount - platform_fee_amount
    --   WITHDRAWAL        -> masuk REKENING BANK/E-WALLET streamer = gross_amount (persis nominal yg diminta)
    net_amount                     BIGINT       NOT NULL,

    gateway_reference_id           VARCHAR(100) NOT NULL,                                                 -- id transaksi internal dari PG (dipakai matching webhook)

    -- Nomor yang TAMPIL ke user/dicari admin: nomor VA, RRN QRIS, dll -- beda dari
    -- gateway_reference_id (itu id internal PG, bukan yang dilihat orang). Nullable karena
    -- tidak semua channel punya nomor tampilan (misal e-wallet redirect tidak punya VA number).
    payment_reference_number       VARCHAR(100),

    raw_payload                    JSONB,                                                                 -- payload webhook mentah, disimpan apa adanya sbg bukti audit

    paid_at                        TIMESTAMPTZ,
    settled_at                     TIMESTAMPTZ,                                                           -- diisi dari tanggal LAPORAN SETTLEMENT asli PG, bukan hasil hitung sendiri
    created_at                     TIMESTAMPTZ  NOT NULL,
    updated_at                     TIMESTAMPTZ  NOT NULL,

    -- Identitas unik "milik gateway config yang mana" -- mencegah webhook diproses dobel (idempotency).
    UNIQUE (gateway_channel_config_id, gateway_reference_id),
    -- Hanya salah satu sumber fee platform yang boleh keisi per transaksi.
    CHECK (platform_fee_rule_id IS NULL OR user_fee_override_id IS NULL)
);

-- =====================================================================
-- INDEXING -- dioptimalkan untuk pageable (offset) list + filter admin dashboard.
--
-- Prinsip: 'id' adalah UUID v7 (time-ordered), jadi ORDER BY id DESC == ORDER BY
-- created_at DESC secara hasil, TAPI index PK sudah otomatis ada (gratis). Maka semua
-- composite index di bawah pakai 'id DESC' sebagai kolom terakhir (BUKAN created_at) --
-- lebih kecil (16 byte UUID vs 8+16 byte kalau dobel timestamp+uuid) dan tetap
-- menghasilkan urutan terbaru->terlama yang sama. created_at TETAP perlu index sendiri
-- HANYA untuk filter date range (butuh bandingkan nilai waktu literal, id tidak bisa
-- dipakai untuk itu tanpa trik tambahan).
--
-- Total 6 index tambahan (di luar PK & UNIQUE constraint yg sudah otomatis punya index) --
-- ini masih wajar untuk tabel finansial (bank/fintech umumnya 5-8 index di tabel transaksi
-- utama). Kalau nanti write throughput jadi masalah, cek pg_stat_user_indexes -- index
-- dengan idx_scan=0 setelah beberapa minggu produksi berarti gak kepake, aman didrop.
-- =====================================================================

-- (1) List tanpa filter, urut terbaru->terlama -> otomatis dari PK, TIDAK perlu index baru.

-- (2) Filter by status (paling sering dipakai ops: "tampilkan yang PENDING/FAILED")
CREATE INDEX idx_transactions_status_id ON payment.transactions (status, id DESC);

-- (3) Filter by type (donation/withdrawal/refund)
CREATE INDEX idx_transactions_type_id ON payment.transactions (type, id DESC);

-- (4) Filter date range -- WAJIB kolom created_at asli, id tidak bisa dipakai untuk ini
CREATE INDEX idx_transactions_created_at ON payment.transactions (created_at DESC);

-- (5) Search exact by gateway_reference_id (id internal PG) -- terpisah dari UNIQUE
-- constraint di atas karena UNIQUE-nya composite (gateway_channel_config_id dulu),
-- jadi TIDAK bisa dipakai untuk cari gateway_reference_id sendirian.
CREATE INDEX idx_transactions_gateway_reference_id ON payment.transactions (gateway_reference_id);

-- (6) Search exact by nomor VA/RRN yang dilihat user. Partial index (WHERE ... IS NOT NULL)
-- -- lebih kecil & lebih murah di-maintain karena kolom ini banyak NULL-nya.
CREATE INDEX idx_transactions_payment_reference_number ON payment.transactions (payment_reference_number)
    WHERE payment_reference_number IS NOT NULL;

-- (7) PARTIAL index utk dashboard monitoring ops ("transaksi yang butuh perhatian" --
-- masih PENDING/PAID tapi belum SETTLED). Subset ini biasanya jauh lebih kecil dari total
-- tabel, jadi partial index ini jauh lebih murah (size & write cost) dibanding index penuh,
-- dan query "cari yang stuck" jadi sangat cepat karena index-nya memang cuma isi baris relevan.
CREATE INDEX idx_transactions_unsettled ON payment.transactions (id DESC)
    WHERE status IN ('PENDING', 'PAID');

-- (8) Lacak balik "transaksi donasi mana yang sudah direfund" -- partial karena kolom ini
-- NULL untuk hampir semua baris (cuma diisi kalau type='REFUND').
CREATE INDEX idx_transactions_refund_original ON payment.transactions (refund_original_transaction_id)
    WHERE refund_original_transaction_id IS NOT NULL;

-- SENGAJA TIDAK dibuat:
--   - Index gabungan (status, type, id) -- kombinasi filter status+type bersamaan jarang
--     terjadi di dashboard admin (biasanya salah satu). Kalau nanti terbukti sering dipakai
--     bareng (cek pg_stat_statements), baru tambah -- jangan bikin index "jaga-jaga".
--   - GIN index di raw_payload (JSONB) -- mahal untuk write, JSONB ini cuma dibaca per-row
--     (buka detail 1 transaksi), bukan buat query/filter across rows.
--   - Index search pakai ILIKE '%...%' (partial text match) -- butuh trigram (pg_trgm) yang
--     lumayan berat untuk write. Search VA/RRN/reference id di dunia nyata SELALU exact match
--     (user paste/scan nomor penuh), jadi btree biasa di atas sudah cukup & jauh lebih murah.


-- ============================ MANUAL ADJUSTMENT ============================
-- Sumber ledger di luar transactions -- bukti bahwa pola reference_type/reference_id
-- generic di journals memang terpakai (nambah sumber baru = 0 perubahan tabel journals/ledger_entries).

CREATE TABLE payment.manual_adjustments
(
    id           UUID PRIMARY KEY,
    wallet_id    UUID         NOT NULL REFERENCES payment.wallets (id),
    direction    VARCHAR(10)  NOT NULL, -- 'DEBIT' | 'CREDIT'
    amount       BIGINT       NOT NULL CHECK (amount > 0),
    reason       VARCHAR(500) NOT NULL, -- wajib diisi, alasan bisnis, WAJIB buat audit trail

    -- Segregation of duty: yang mengajukan tidak boleh sama dengan yang approve.
    -- Standar compliance (mirip prinsip SOX) supaya 1 admin tidak bisa diam-diam nambah saldo sendiri.
    requested_by UUID         NOT NULL, -- identity.users.id (admin pengaju)
    approved_by  UUID,                  -- identity.users.id (admin approver), NULL sampai di-approve

    status       VARCHAR(20)  NOT NULL, -- 'PENDING' | 'APPROVED' | 'REJECTED'
    created_at   TIMESTAMPTZ  NOT NULL,
    approved_at  TIMESTAMPTZ,

    CHECK (requested_by IS DISTINCT FROM approved_by)
);