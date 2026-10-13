-- Database-level init run once by the postgres container on first boot.
-- Flyway handles all schema DDL — this file only creates extensions
-- that Flyway migrations depend on.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements";
