#!/bin/sh
# Payment service's own database and user. Postgres runs this only on an empty volume;
# for an existing one: docker compose exec postgres sh /docker-entrypoint-initdb.d/20-payment-db.sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v db="$PAYMENT_DB" -v user="$PAYMENT_DB_USER" -v password="$PAYMENT_DB_PASSWORD" <<'SQL'
CREATE USER :"user" WITH PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"user";
-- Only its owner may connect (the backend's user is a superuser, so this keeps out everyone else)
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
SQL
