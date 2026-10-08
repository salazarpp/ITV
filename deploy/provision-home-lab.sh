#!/usr/bin/env bash
# Operator-only database provisioning. Do not invoke from CI or deployment.
set -euo pipefail

if [[ "$(hostname)" != "jl-S" ]]; then
  printf 'Run this provisioning command on jl-S.\n' >&2
  exit 1
fi
command -v gh >/dev/null
command -v openssl >/dev/null
gh auth status >/dev/null 2>&1

# Stop on existing application data rather than changing credentials silently.
docker exec -i postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d postgres' <<'SQL'
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'pokesync')
     OR EXISTS (SELECT 1 FROM pg_database WHERE datname = 'pokesync') THEN
    RAISE EXCEPTION 'PokeSync role or database already exists; inspect it before provisioning';
  END IF;
END
$$;
SQL

POKESYNC_DB_PASSWORD="$(openssl rand -base64 32)"
export POKESYNC_DB_PASSWORD
trap 'unset POKESYNC_DB_PASSWORD' EXIT

# Store the password before provisioning so failure does not discard its only copy.
printf '%s' "$POKESYNC_DB_PASSWORD" | gh secret set DB_PASSWORD --env k8s-dev --repo salazarpp/ITV

docker exec -i -e POKESYNC_DB_PASSWORD postgres sh -c 'exec psql -X -v ON_ERROR_STOP=1 -U "${POSTGRES_USER:-postgres}" -d postgres' <<'SQL'
\getenv app_password POKESYNC_DB_PASSWORD
SELECT format('CREATE ROLE pokesync LOGIN PASSWORD %L', :'app_password') \gexec
CREATE DATABASE pokesync OWNER pokesync;
SQL

printf 'PokeSync database and role created; matching GitHub environment secret updated.\n'
