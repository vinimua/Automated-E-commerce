-- Init script: ensure dev database extras
-- The main schema is managed by Flyway in api-java.
-- This file only sets up any dev-only configuration.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create the temporal database for Temporal Server
CREATE DATABASE temporal;
GRANT ALL PRIVILEGES ON DATABASE temporal TO tk_user;

-- Grant all on the dev database (Flyway will create tables later)
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO tk_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO tk_user;
