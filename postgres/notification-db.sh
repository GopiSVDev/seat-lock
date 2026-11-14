#!/bin/sh
# Notification service's own database and user. Postgres runs this only on an empty volume;
# for an existing one: docker compose exec postgres sh /docker-entrypoint-initdb.d/30-notification-db.sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v db="$NOTIFICATION_DB" -v user="$NOTIFICATION_DB_USER" -v password="$NOTIFICATION_DB_PASSWORD" <<'SQL'
CREATE USER :"user" WITH PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"user";
-- Only its owner may connect
REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
SQL
