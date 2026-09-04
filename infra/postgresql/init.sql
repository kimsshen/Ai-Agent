-- Run this script after connecting to the local industrial_ai database.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- push_log is an existing business table. Create analysis indexes only when
-- the business table is present, so this script remains safe for vector-only DBs.
DO $$
BEGIN
    IF to_regclass('public.push_log') IS NOT NULL THEN
        CREATE INDEX IF NOT EXISTS idx_push_log_channel_creation_date
            ON push_log (channel, creation_date);
        CREATE INDEX IF NOT EXISTS idx_push_log_creation_date
            ON push_log (creation_date);
    END IF;
END $$;
