#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 3 ]; then echo "Usage: bash scripts/backup-db.sh ENV_FILE COMPOSE_PROJECT OUTPUT.dump" >&2; exit 2; fi
root="$(cd "$(dirname "$0")/.." && pwd)"
env_file="$1"
project="$2"
output="$3"
umask 077
if [ -e "$output" ]; then echo "Refusing to overwrite backup" >&2; exit 2; fi
mkdir -p "$(dirname "$output")"
temporary="${output}.partial"
trap 'rm -f "$temporary"' EXIT
docker compose --env-file "$env_file" -p "$project" -f "$root/infra/compose.prod.yml" exec -T postgres     sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' > "$temporary"
test -s "$temporary"
mv "$temporary" "$output"
echo "Backup created: $output"
