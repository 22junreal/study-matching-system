#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 4 ]; then echo "Usage: bash scripts/restore-db.sh ENV_FILE COMPOSE_PROJECT INPUT.dump NEW_DATABASE" >&2; exit 2; fi
root="$(cd "$(dirname "$0")/.." && pwd)"
env_file="$1"
project="$2"
input="$3"
target_db="$4"
[[ "$target_db" =~ ^[a-z][a-z0-9_]{0,50}$ ]] || { echo "Invalid database name" >&2; exit 2; }
test -s "$input"
compose=(docker compose --env-file "$env_file" -p "$project" -f "$root/infra/compose.prod.yml")
# createdb fails if the target exists. Never drop or overwrite an existing database.
"${compose[@]}" exec -T postgres sh -c 'test "$1" != "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$1"' sh "$target_db"
"${compose[@]}" exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --no-acl --exit-on-error' sh "$target_db" < "$input"
echo "Restored into new database: $target_db"
