#!/usr/bin/env bash
# Move a manually started stack (Compose project OLD, default "docker") to the
# project name used by the CD pipeline ("paypink") without losing data.
#
# - Removes OLD's containers and network (volumes are NOT deleted; they stay as a backup).
# - Copies each OLD_<vol> volume into paypink_<vol>, wiping anything a failed run put there.
# Run on the VM as root, e.g. from Cloud Shell:
#   az vm run-command invoke -g RG-PAYPINK-WESTUS2 -n vm-paypink \
#     --command-id RunShellScript --scripts @04-migrate-compose-project.sh
# Expect downtime until the pipeline (or a manual `up`) starts the paypink stack.
set -euo pipefail

OLD="${OLD_PROJECT:-docker}"
NEW="paypink"
VOLUMES="azuresql_data postgres_data kafka_data zookeeper_data zookeeper_log loki_data tempo_data"

echo "== Stopping and removing containers of project '$OLD' (volumes kept) =="
docker ps -aq --filter "label=com.docker.compose.project=$OLD" | xargs -r docker rm -f
docker network ls -q --filter "label=com.docker.compose.project=$OLD" | xargs -r docker network rm || true

for v in $VOLUMES; do
  src="${OLD}_${v}"; dst="${NEW}_${v}"
  if ! docker volume inspect "$src" >/dev/null 2>&1; then
    echo "skip $src (not found)"; continue
  fi
  docker volume inspect "$dst" >/dev/null 2>&1 || docker volume create \
    --label com.docker.compose.project="$NEW" --label com.docker.compose.volume="$v" "$dst" >/dev/null
  echo "== Copy $src -> $dst =="
  docker run --rm -v "$src":/from:ro -v "$dst":/to alpine sh -c \
    'find /to -mindepth 1 -delete && cp -a /from/. /to/ && du -sh /from /to'
done

echo "== Done. Old volumes ${OLD}_* are kept as a backup; delete them once the paypink stack is verified. =="
