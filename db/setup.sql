-- One-time database provisioning. Run as a PostgreSQL administrator, e.g.:
--   psql -h localhost -U postgres -v app_password='choose-a-strong-password' -f db/setup.sql
-- Tables are created later by Flyway migrations when the application starts.

CREATE ROLE app_user LOGIN PASSWORD :'app_password'
    NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;

CREATE DATABASE app_db OWNER postgres;
REVOKE ALL ON DATABASE app_db FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE app_db TO app_user;

\connect app_db

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Application schema (development) and a separate schema for integration tests.
CREATE SCHEMA app_schema_dev AUTHORIZATION app_user;
CREATE SCHEMA app_schema_test AUTHORIZATION app_user;
