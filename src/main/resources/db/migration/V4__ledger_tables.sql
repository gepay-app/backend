CREATE SCHEMA IF NOT EXISTS ledger;

-- =====================================================================
-- LEDGER (generic, tanpa FK ke payment)
-- =====================================================================

-- Daftar akun (chart of accounts) + sub-account per owner. Ini "wadah" saldo.
-- Kontrol (2300,4000,...) owner NULL; sub-account owner_ref diisi (user/provider).
CREATE TABLE ledger.accounts
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(20)  NOT NULL,               -- kode akun, mis. '1100' (lihat Chart of Accounts)
    name           VARCHAR(120) NOT NULL,               -- nama tampil akun
    type           VARCHAR(10)  NOT NULL,               -- ASSET | LIABILITY | EQUITY | REVENUE | EXPENSE
    normal_balance VARCHAR(6)   NOT NULL,               -- DEBIT | CREDIT (arah saldo bertambah)
    owner_type     VARCHAR(24),                         -- CREATOR | PAYMENT_PROVIDER | PAYOUT_PROVIDER | BANK | NULL
    owner_ref      VARCHAR(64),                         -- UUID user / id provider / kode bank (generic text)
    currency       CHAR(3)      NOT NULL DEFAULT 'IDR', -- hanya IDR (single currency)
    balance        BIGINT       NOT NULL DEFAULT 0,     -- saldo diarah normal, cache dari entries
    version        BIGINT       NOT NULL DEFAULT 0,     -- optimistic lock; naik tiap update saldo
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_accounts_code_owner UNIQUE NULLS NOT DISTINCT (code, owner_type, owner_ref)
);
CREATE INDEX ix_accounts_owner ON ledger.accounts (owner_type, owner_ref);

-- Header 1 kejadian finansial. `idempotency_key` mencegah 1 event diposting 2x.
-- TIDAK pernah di-UPDATE/DELETE; koreksi lewat journal baru (`reverses_journal_id`).
CREATE TABLE ledger.journals
(
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key     VARCHAR(160) NOT NULL,                  -- mis. 'payment:PAY-1:paid' (unik)
    reference_type      VARCHAR(40)  NOT NULL,                  -- PAYMENT | SETTLEMENT | WITHDRAWAL | PAYOUT
    -- | FUND_TRANSFER | REFUND | ADJUSTMENT
    reference_id        VARCHAR(64)  NOT NULL,                  -- id sumber (pay-1/set-1/wd-1/...), teks generic
    description         VARCHAR(255) NOT NULL,                  -- teks untuk manusia, bukan untuk agregasi
    occurred_at         TIMESTAMPTZ  NOT NULL,                  -- waktu bisnis kejadian (beda dengan created_at)
    reverses_journal_id BIGINT REFERENCES ledger.journals (id), -- diisi kalau ini jurnal koreksi
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),    -- waktu sistem mencatat
    CONSTRAINT ux_journals_idem UNIQUE (idempotency_key)
);
CREATE INDEX ix_journals_ref ON ledger.journals (reference_type, reference_id);

-- Baris debit/credit. 1 journal harus punya >=2 baris dan SUM(DEBIT)=SUM(CREDIT).
-- Append-only; `amount` selalu positif, arah ditentukan `direction`.
CREATE TABLE ledger.entries
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_id BIGINT      NOT NULL REFERENCES ledger.journals (id),
    account_id BIGINT      NOT NULL REFERENCES ledger.accounts (id),
    direction  VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount     BIGINT      NOT NULL CHECK (amount > 0), -- nominal IDR (bulat, > 0)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_entries_journal ON ledger.entries (journal_id);
CREATE INDEX ix_entries_account ON ledger.entries (account_id, id);