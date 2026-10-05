#!/usr/bin/env bash
# Dump the felege_selam database to a timestamped, gzipped file and keep the newest 14.
#
# Usage:   sudo /opt/membertracker/deploy/backup-mysql.sh
# Cron:    0 2 * * * /opt/membertracker/deploy/backup-mysql.sh >> /var/log/membertracker-backup.log 2>&1   (root's crontab)
#
# The root password comes from MYSQL_ROOT_PASSWORD in the environment, or else from the
# MYSQL_ROOT_PASSWORD= line of /opt/membertracker/.env (the file docker-compose.yml reads).
# Optional environment: ENV_FILE (default /opt/membertracker/.env), BACKUP_DIR
# (default /srv/membertracker/backups), KEEP (how many backups to keep, default 14).
# A backup on this machine does not survive losing the machine: copy the folder elsewhere too.
# To restore, see docs/deploy-linux.md.
set -euo pipefail

ENV_FILE="${ENV_FILE:-/opt/membertracker/.env}"
BACKUP_DIR="${BACKUP_DIR:-/srv/membertracker/backups}"
KEEP="${KEEP:-14}"
CONTAINER=membertracker-mysql
DATABASE=felege_selam

fail() {
  echo "backup-mysql: $*" >&2
  exit 1
}

# Password: the environment wins; otherwise read the file without running it.
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
  [ -r "$ENV_FILE" ] || fail "MYSQL_ROOT_PASSWORD is not set and $ENV_FILE cannot be read (run as root, or set ENV_FILE)"
  MYSQL_ROOT_PASSWORD="$(sed -n 's/^MYSQL_ROOT_PASSWORD=//p' "$ENV_FILE" | tail -n 1 | tr -d '\r' | sed -e "s/^[\"']//" -e "s/[\"']\$//")"
fi
[ -n "$MYSQL_ROOT_PASSWORD" ] || fail "no MYSQL_ROOT_PASSWORD found in $ENV_FILE"
export MYSQL_ROOT_PASSWORD

[[ "$KEEP" =~ ^[1-9][0-9]*$ ]] || fail "KEEP must be a whole number of 1 or more, got '$KEEP'"
command -v docker > /dev/null || fail "docker is not installed or not in PATH"
[ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2> /dev/null)" = "true" ] \
  || fail "container $CONTAINER is not running (check: docker ps)"

umask 077
mkdir -p "$BACKUP_DIR" || fail "cannot create $BACKUP_DIR"

stamp="$(date +%Y%m%d-%H%M%S)"
final="$BACKUP_DIR/$DATABASE-$stamp.sql.gz"
partial="$final.part"
trap 'rm -f "$partial"' EXIT

# MYSQL_PWD is passed by name only, so the password never shows in the process list.
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" docker exec -e MYSQL_PWD "$CONTAINER" \
  mysqldump -uroot --single-transaction --routines "$DATABASE" | gzip > "$partial" \
  || fail "mysqldump failed; no backup was written and older backups were kept"

gzip -t "$partial" || fail "the dump is not a valid gzip file; older backups were kept"
[ "$(gzip -dc "$partial" | wc -c)" -gt 1000 ] || fail "the dump is almost empty; older backups were kept"
mv "$partial" "$final"

# Delete everything but the newest $KEEP (the timestamp in the name sorts oldest first).
find "$BACKUP_DIR" -maxdepth 1 -name "$DATABASE-*.sql.gz" | sort -r | tail -n +"$((KEEP + 1))" | while read -r old; do
  rm -f -- "$old"
done

echo "backup-mysql: wrote $final ($(wc -c < "$final") bytes), keeping the newest $KEEP"
