#!/usr/bin/env bash
# PayPink 2.0 - Step 2: prepare the Ubuntu 24.04 VM.
# Run ON THE VM as azureuser (not root):  ./02-bootstrap-vm.sh
set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this as a normal user (e.g. azureuser), not root." >&2
  exit 1
fi

echo "== Updating packages =="
sudo apt-get update -y
sudo DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg git openssl

echo "== 4 GB swap (safety net, not a substitute for RAM limits) =="
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 4G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "== Docker Engine + Compose plugin =="
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Keep container logs from filling the disk
sudo tee /etc/docker/daemon.json >/dev/null << 'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
sudo systemctl restart docker

# Docker access for this user only. Do NOT chmod 666 /var/run/docker.sock.
sudo usermod -aG docker "$USER"

echo "== App folder and secrets file =="
sudo mkdir -p /opt/paypink
sudo chown "$USER":"$USER" /opt/paypink
ENV_FILE=/opt/paypink/.env
if [ ! -f "$ENV_FILE" ]; then
  gen() { openssl rand -base64 24 | tr -d '/+=\n' | cut -c1-24; }
  cat > "$ENV_FILE" << ENVEOF
# Generated on the VM. Never commit this file.
# Rename the variables to match your docker-compose.yml.
MSSQL_SA_PASSWORD=$(gen)Aa1!
POSTGRES_PASSWORD=$(gen)
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
# Memory limits so ~20 containers fit in 16 GB
MSSQL_MEMORY_LIMIT_MB=3072
JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60
ENVEOF
  chmod 600 "$ENV_FILE"
  echo "Created $ENV_FILE with generated secrets."
else
  echo "$ENV_FILE already exists, leaving it alone."
fi

cat << 'MSG'

Bootstrap finished.

1) Log out and back in so the docker group applies:   exit ; ssh azureuser@<your-vm>
   Then check:  docker run --rm hello-world

2) Install the GitHub self-hosted runner (as azureuser, NOT root):
   - Repo > Settings > Actions > Runners > New self-hosted runner > Linux x64
   - Copy the exact download + config commands GitHub shows (do not reuse an old version number)
   - Add the labels:  --labels "self-hosted,azure-vm"
   - Then:  sudo ./svc.sh install azureuser && sudo ./svc.sh start
   - If you added the docker group AFTER installing the runner:  sudo ./svc.sh stop && sudo ./svc.sh start
   - Keep the repository PRIVATE.

3) Put your compose project in /opt/paypink (the pipeline checks it out and runs
   `docker compose -p paypink --env-file /opt/paypink/.env up -d --build`).
MSG
