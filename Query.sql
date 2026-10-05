BEGIN;

-- 1. Hapus schema 'identity' dan 'ledger' beserta seluruh succeeds/isinya (CASCADE)
DROP SCHEMA IF EXISTS identity CASCADE;
DROP SCHEMA IF EXISTS ledger CASCADE;
DROP SCHEMA IF EXISTS payment CASCADE;

-- 2. Hapus semua tabel yang ada di dalam schema 'public'
DO $$
    DECLARE
        r RECORD;
    BEGIN
        FOR r IN (
            SELECT tablename
            FROM pg_tables
            WHERE schemaname = 'public'
        ) LOOP
                EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
            END LOOP;
    END $$;

COMMIT;